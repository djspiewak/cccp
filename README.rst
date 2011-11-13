This project provides a generic server and sub-process agent for implementing
cross-editor real-time, character by character collaboration (a la SubEthaEdit,
Gobby or Google Docs).  Support is currently provided for the following editors:

* jEdit_

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
* ``(swank:edit-file file-name (...))``

  This is the most important API call.  This call should be made on every buffer
  change.  The inner-form is the description of the buffer change and must be an
  ordered property list of the form ``(:key1 value1 :key2 value2)``.  The exact
  schema for this property list should be as follows:
  
  * ``:retain`` – Must correspond to an integer value.  Specifies an offset into the file.
  * ``:insert`` – Must correspond to a string value.  Specifies a text string to insert at the current location.
  * ``:delete`` – Must correspond to a string value.  Specifies a text string to delete from the current location.
  
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

*TODO*


.. _jEdit: http://jedit.org
.. _SBT: https://github.com/harrah/xsbt/wiki
.. _BlueEyes: https://github.com/jdegoes/blueeyes
.. _Netty: http://www.jboss.org/netty
.. _OT: http://www.codecommit.com/blog/java/understanding-and-applying-operational-transformation
.. _this article on OT: http://www.codecommit.com/blog/java/understanding-and-applying-operational-transformation
.. _the documentation: http://wave-protocol.googlecode.com/hg/whitepapers/operational-transform/operational-transform.html
