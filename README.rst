This project provides a generic server and sub-process agent for implementing
cross-editor real-time, character by character collaboration (a la SubEthaEdit,
Gobby or Google Docs).  Support is currently provided for the following editors:

* jEdit_
* Emacs_ (via the cccp-mode_)

The editor-specific plugins are extremely tiny and merely delegate all work to an
editor-agnostic sub-process.  This means that it should be extremely easy to
add CCCP support to almost any editor that supports extension.

Currently, the only functionality provided is character-by-character simultaneous
co-edit between any number of editors.  Future functionality may include things
like:

* **Buffer set linking** – This would allow collaborators to "follow" each-other's
  actions not just in terms of edits, but in terms of active buffers and their
  respective positions.  In this case, one collaborator would be the "master"
  while the others followed along.
* **File discovery** – Rather than having to share an opaque file identifier, the
  server would expose a list of active files to the editors, allowing users to
  link their buffers by selecting the appropriate file from a list.
* **Commit Coordination** – At present, there is no VCS-specific support.  This
  means that when you're ready to commit, you must nominate just one person to
  do the commit and then sync back up again.  This can be a pain.  With certain
  VCS (like Git), it would be possible to run the commit through CCCP and perform
  the same commit (with the same author information) simultaneously on *both* ends,
  resulting in the same commit being shared between collaborators without a separate
  ``fetch`` action.


Usage
=====

If you want to use CCCP, you will first need to build both the ``server`` and
``agent`` modules.  This can be done using the ``stage`` task in the SBT_ build.
This task will create a ``dist/`` directory under whatever project you use it
against.  Inside this directory will be *all* of the dependencies required to
run that particular module, as well as a pair of scripts (for Windows and *nix)
which launch that process.  If you don't have SBT on your system, you can find
instructions on how to get running here: https://github.com/harrah/xsbt/wiki/Getting-Started-Setup.
Essentially all you need to do is launch the ``sbt-launch.jar`` file in the project
directory.

The server process is a very straight forward HTTP server built on BlueEyes_,
which is in turn built on Netty_.  When you launch its process, you will need to
pass the ``--configFile <file>`` option, where ``<file>`` is replaced with your
server configuration file.  The following template should be sufficient::
    
    server {
      port = 8585
      sslPort = 8586
    }
    
(note: you can find these contents in the ``default.conf`` file in the server
module)

The server will remain up and running until killed with Ctrl+C.  Note that Netty
will reject the ``HUP`` signal if there are still outstanding client connections.
You should run the server in some place that is accessible to all clients involved.
All edit operations will be proxied through this server, which is handling the
server-side OT_ for CCCP.  You can find more details on this process below.

The second module you will need to build is the agent.  This is the editor agnostic
sub-process that will be *used* by any editor-specific plugins providing CCCP
functionality.  You do not run this process directly.  Just build it and take note
of its output directory.

Once you have all of this setup, you can now configure your editor-specific plugin.
This will involve entering the root directory of the agent build (this should be
``dist/`` in the agent module directory) as well as the protocol, host and port
to be used to connect to the server.

If everything is working, you should be able to link a buffer in your editor.
This process will prompt you for an id for that buffer.  This id is used to
identify the file you are linking so that collaborators need not use the exact
same file names.  Linking the file merely creates the registration with the server,
it does not upload any data!

With the file linked, you can begin editing.  Other collaborators can link with
the same id.  The critical restriction is that they must *start* from exactly the
same file state as you did when you first linked the file.  One good way to ensure
this is to make everyone start from the same clean Git revision.  Any edits you
have performed since linking will be sent down to the new collaborators the moment
they link their buffers.

After this point, all edits will be synced character-by-character between all
linked buffers.  Collaborators can type simultaneously at different points (or
even the *same* point) in the file.  Conflicts are resolved by the server in a
uniform way, so the protocol never "fails".  If your connection is more latent
than your keyboard, edits may be chunked together slightly.  This is entirely
normal.  The chunk sizes will automatically adjust themselves *immediately* in
response to the network latency between your agent and the server, making the
protocol extremely self-healing.  You can even disconnect entirely from the server,
perform a large number of edits, and they will all be synced in a large chunk
as soon as your connection is re-established.

There is one important restriction here: you *cannot* change files outside of the
editor.  If you do this, the collaboration will get out of sync and the server
will reject your changes.  If you want to change a file outside the editor, you
will need to unlink that buffer, change the file and then relink when you are done.


jEdit
-----

*TODO*


Agent Protocol
==============

The agent protocol is based on SWANK, which is the protocol used by SLIME_ and
ENSIME_ to communicate with Emacs.  The essence of the protocol is just sending
s-expressions over a raw socket with run-length prefixes.  The best description
I've found of this process is from the ENSIME manual:

    To send an s-expression, determine its ASCII length and then encode that
    integer as a padded six-digit hexadecimal value. Write this value to the
    output socket first, then followed by the ASCII form of the s-expression. On
    the receiving side, the reader loop should read six ASCII characters into a
    buffer and convert that into an integer, then read that number of ASCII
    characters from the socket, parsing the result into an s-expression.
    
    .. image:: http://aemon.com/file_dump/wire_protocol.png
    
Each SWANK RPC call is of the following form::
    
    (:swank-rpc <form> <call-id>)
    
For example, if you wanted to invoke the ``edit-file`` RPC as call id 42, the
s-expression would look like the following::
    
    (:swank-rpc (swank:edit-file "file.txt" (:retain 4 :insert "ing" :retain 1)) 42)
    
The actual ASCII bytes sent over the socket would be as follows::
    
    000050(:swank-rpc (swank:edit-file "file.txt" (:retain 4 :insert "ing" :retain 1)) 42)
    
The call id should be unique for each RPC invocation, but beyond that it has no
restrictions.  Returns for a particular call will use its call id, though this
feature is not relevant for CCCP as none of the calls have returns.

Invocations from the agent to the editor are less restricted.  Generally, they can
be of any agreed-upon form.  They still use run-length prefixing and s-expressions,
but beyond that any form is allowed.  See the Editor API.

Agent API
---------

* ``(swank:init-connection (:protocol protocol :host host :port port))``
  
  Initializes the agent's connection to the server.  Note that the agent will
  not actually test this connection, it will merely configure for later HTTP calls.
  This RPC *must* be invoked prior to anything else and may only be called once.
* ``(swank:link-file id file-name)``

  Creates a new buffer linkage for a particular identifier.  This identifier will
  be used whenever the agent sends operations on this buffer to the server.  Thus,
  if you want to link a buffer between two editors, you would simply link them
  both to the same identifier.  The file name is only significant in that it must
  be the file name included in the ``swank:edit-file`` invocations which perform
  the actual edits.  This is done so that the editor plugin does not have to
  maintain its own internal mapping from file names to identifiers.
  
  This call must be made prior to editing the file and can only be made once.
  Note that it is possible to relink buffers after having previously unlinked
  them.  However, this requires that the buffer be in *exactly* the same state as
  any buffers that remained linked, or the same state as the last buffer to be
  unlinked at the point at which it was unlinked.  Generally speaking, it is just
  safer to link on a fresh identifier when relinking a buffer.
* ``(swank:unlink-file file-name)``
  
  Removes a linkage for a particular file.  Remote updates will not be
  propagated to the buffer once this call has run.  This also frees any resources
  in the agent that are associated with the linkage.  Please note that in cases
  of high-latency, there may be changes local to the agent that have not yet
  transmitted to the server.  These changes will *not* be sent if ``unlink-file``
  happens before such time as that is possible.  The editor local buffer will
  still have the changes, but they will never reach the server.
* ``(swank:edit-file file-name (...))``

  This is the most important API call.  This call should be made on every buffer
  change.  The inner-form is the description of the buffer change and must be an
  ordered property list of the form ``(:key1 value1 :key2 value2)``.  The exact
  schema for this property list should be as follows:
  
  * ``:retain`` – Must correspond to an integer value.  Specifies an offset into
    the file.
  * ``:insert`` – Must correspond to a string value.  Specifies a text string to
    insert at the current location.
  * ``:delete`` – Must correspond to a string value.  Specifies a text string to
    delete from the current location.
  
  There are a few things that are important to understand about this format.  First,
  the offsets must span the *entire* file.  Thus, if you add up all of the ``:retain``
  values, plus the length of the ``:insert`` and ``:delete`` strings, it must
  equal the total character length of the buffer.  In the case of ``:insert``, this
  is the total length *after* application of the operation; in the case of ``:delete``,
  it is the total length *before* application of the operation.  Note that this
  metaphor only makes sense if you have either an ``:insert`` or a ``:delete``,
  but not both.  This is a weakness in the line of thought, since it is very
  possible to have an operation which performs both actions (e.g. if text is selected
  and replaced with some new text in an atomic action).  A truer way of looking at
  operation offsets would be to view the operation as an ordered set of instructions
  to a cursor walking through the buffer from start to finish.  The cursor *must*
  traverse the entire document.
  
  Note that operations sent from the editor to the agent are likely to be single-action
  operations with a leading and trailing retain.  This is extremely *unlikely* to
  be the case for operations coming from the agent to the editor.  This is because
  the protocol composes operations together when latency exceeds typist speed (the
  normal mode of operation).  As a result, the editor code which handles operations
  must be able to handle multiple actions in a single operation.  For example:
  
  ``(:retain 4 :delete "bar" :insert "foo" :retain 127 :insert "baz" :retain 10)``
  
  The jEdit plugin handles this by converting each ``:delete`` and ``:insert``
  action into its own separate operation with offset and contents.  These actions
  are then applied *in order* (the ordering bit is very important, otherwise the
  offsets will not be correct for actions subsequent to the first in the operation).
  
  Just to give an example of an operation, we would insert the text ``here`` at
  offset ``11133`` with a total buffer length of ``11430`` using the following
  operation:
  
  ``(:retain 11133 :insert "here" :retain 297)``
  
  It is very important that operation application and synthesis is implemented
  correctly in the editor-specific plugins.  Bugs in this code will result in
  incorrectly-synchronized buffers and errors in the agent, the server, or both.
  For more details on operations, see `this article on OT`_ as well as `the documentation`_
  at http://www.waveprotocol.org.  CCCP does not implement the Wave protocol,
  but it does use Wave's OT algorithms and operation abstractions.
* ``(swank:shutdown)``
  
  Causes the agent process to gracefully shutdown.  This call should be used
  instead of just killing the sub-process.  While killing the process will *work*,
  the ``swank:shutdown`` call gives the agent a chance to clean up registrations
  on the server.


Gory Details
============

CCCP fully implements an optimistic concurrency control mechanism called "operational
transformation".  This is what allows real-time collaborative editing on a single
document to proceed without each editor waiting for a server round-trip before
inserting or removing characters.  Before we dive into how this works, we need
to establish a little vocabulary:

* **operation** – a command to change the edit buffer consisting of zero or more
  *actions* applied in a cursor style, spanning the entire buffer
* **action** – an individual component of an *operation*, indicating that text
  should be added or removed (depending on the action type)
* **transformation** – the process of adjusting or "fixing" operations to that
  they can be reordered between clients without affecting the net composite
* **composition** – the process of taking two operations that apply to the same
  document and deriving one operation which represents the net change of the two
  when applied to the original document
* **client** – the editor itself
* **agent** – the editor sub-process which handles the client-side work
* **server** – the server process which handles the server-side work
* **document** – a term I will use interchangably with *edit buffer*

The fundamental problem with real-time collaborative editing is that changes are
occuring simultaneously at various positions in the document.  Each editor needs
to apply its operations locally without delay.  This is a critical "feature" as
it is what allows input responsiveness in the client.  Unfortunately, if editor
**A** inserts two characters at offset 12 while simultaneously editor **B** inserts
five characters at offset 20, there is potential for document corruption.

This is really the classic diamond problem in concurrency control.  Editor **A**
applies its operation locally and sends it to **B**.  Meanwhile, editor **B**
applies its operation locally and sends it to editor **A**.  However, when editor
**A** attempts to apply the operation from editor **B**, it will perform the
insertion at offset 20, which is *not* the location in the document that **B**
intended.  The actual intended location has become offset 22 due to the two new
characters inserted by **A** prior to receiving the operation from **B**.  This
is the problem that OT solves.

The first step in solving this problem is to handle the simple diamond problem
illustrated above.  Two editors apply operations *a* and *b* simultaneously.
We need to derive two transformed operations *a'* and *b'* such that *a + b'* =
*b + a'*.  This process is mostly just adjusting offsets and shuffling text in
one direction or another, and it is fully implemented by the Wave OT algorithm.
The exact details of this process are beyond the scope of this README.

There is one slight niggling detail here: what happens if we have *three* editors,
**A**, **B** and **C**?  A key insight of the Jupiter collaboration system (the
primary theoretical foundation for Wave) is that it is possible to collapse this
problem into the two-editor case by introducing a client-server architecture.
Effectively, there are only ever two editors at a time: the client and the server.
When operations are applied on the server, they are mirrored back to every other
client.  This also provides a uniform way of resolving conflicts: just find in
favor of the server every time.  Naturally, this is a race condition, and it may
result in unexpected document states surrounding simultaneous edits at the *same*
offset, but the point is that the document states will be uniform across *all*
clients, and so users are able to simply cursor back and "fix" the change as they
see fit.

Unfortunately, solving the one-step diamond is insufficient to enable real-time
collaborative editing.  The reason for this is best illustrated with an example.
Editor **A** applies an operation *a1* and then immediately follows it up with *a2*.
Perhaps **A** is typing at more than one or two characters per second.  Meanwhile,
the server has applied an operation from editor **B**, *b1*.  **A** sends *a1* to
the server while the server simultaneously sends *b1* to **A**.  This will result
in an application of OT to derive *a1'* (on the server) and *b1'* (on the client),
and that's all well and good.  However, **A** also needs to send operation *a2*
to the server, and this is where we hit a snag.

The problem is that *a2* is an operation that applies to the document state following
*a1*, *not* respecting *b1*!  Thus, *a2* requires a document state that the server
does not have.  **A** will send *a2* to the server and the server will be unable
to apply, transform or otherwise make use of the operation, resulting in editor
state corruption.

There are two ways to solve this problem.  The first, and the one used by Jupiter
and almost every other OT-based collaborative system is for the server to track
every individual client's state in vector space.  Basically, the server must not
only apply *a1'* to its internal state, it must also apply *a1* to an *earlier*
state, creating an in-memory fork of the server state that will be preserved until
**A** comes back into sync with the server.  In the case where multiple editors
are typing simultaneously, this could potentially take a very long time.  The
*normal* state for editors using OT is to be walking entirely different state
spaces from each other, only coming back into full sync once everything "calms down".
This produces a very nice user experience, but it also means that the server
would need to track the full (and potentially lengthy) histories for every single
client, producing a large amount of overhead.

This doesn't scale well.  Google's key innovation with Wave was to restrict client
behavior so that **A** can never send *a2* directly to the server.  Instead, **A**
must wait for the confirmation that the server has applied *a1*, at which point
**A** will use the operations it has received from the server in the interim to
infer the current state of the server's document and edit history.  Using this
information, **A** will transform *a2* into *a2'* and send *that* operation to
the server.  Now, the server may still need to transform *a2'* against subsequent
operations that hadn't been received by **A** at the time of transmission, but
that's not a problem.  As long as *a2'* is rooted in server state space, the
server will be able to perform this transformation and will only need to track
its own history.

In terms of version control systems, you can think of this like the clients
constantly rebasing their history against a central repository, rather than pushing
an *entire* branch and attempting to merge at the end.  It's a great deal more
work for the clients, but it means that the server only needs to maintain a
linear history, regardless of the number of clients.

Unfortunately, Wave doesn't provide this for us.  Its code for this purpose is
Wave-specific, and so cannot be repurposed for other things.  For this reason,
CCCP has to provide its own implementation of this logic (``state.scala``).

At the end of the day, the result is a collaborative editing system
that allows character-by-character changes to be shared in real time across *any*
number of clients with varying latencies.  The protocol heals itself and degrades
gracefully, chunking together updates when the server is taking a long time to
report back with the confirmation of the previous operation.  This self-healing
is so flexible that you can actually take your editor completely offline for any
length of time!  The edits will simply buffer up, awaiting confirmation.  Once
the network connection is reestablished, the confirmation will finally arrive,
the buffer will flush to the server in one chunk and everything will sync-up once
again.


.. _jEdit: http://jedit.org
.. _Emacs: http://www.gnu.org/s/emacs/
.. _cccp-mode: https://github.com/candera/cccp-mode
.. _SBT: https://github.com/harrah/xsbt/wiki
.. _BlueEyes: https://github.com/jdegoes/blueeyes
.. _Netty: http://www.jboss.org/netty
.. _OT: http://www.codecommit.com/blog/java/understanding-and-applying-operational-transformation
.. _this article on OT: http://www.codecommit.com/blog/java/understanding-and-applying-operational-transformation
.. _the documentation: http://wave-protocol.googlecode.com/hg/whitepapers/operational-transform/operational-transform.html
