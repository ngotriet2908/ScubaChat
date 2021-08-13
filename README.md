# Scuba chat

Welcome to the README of the Scuba chat application!

# Running the application

<p>
The application requires Java 11 in order to run. Moreover, it uses the JavaFX 
library which is not included in the standard Java SDK distribution.
</p>
<p>
JavaFX and several icon packs are included in the "lib" folder of the application.
The following should be added to the classpath of the project (replace <code>OS</code> with 
<code>win</code> or <code>mac</code> depending on your operating system):
</p>

<ul>
<li><code>lib/OS/javafx-sdk-11.0.2/lib</code> (entire folder)</li>
<li><code>lib/ikonli-core-11.2.0.jar</code></li>
<li><code>lib/ikonli-javafx-11.2.0.jar</code></li>
<li><code>lib/ikonli-fontawesome-pack-11.2.0.jar</code></li>
<li><code>lib/ikonli-fontawesome5-pack-11.2.0.jar</code></li>
</ul>

<p>
Also, the followind line should be added to running configuration of the 
project (replace <code>OS</code> with <code>win</code> or <code>mac</code> depending operating system): 
</p>
<p><code>--module-path lib/OS/javafx-sdk-11.0.2/lib --add-modules=javafx.controls,javafx.fxml</code>
</p>

<p>After all the necessary libraries are configured, the project can be started
by running <b>ChatGUI</b>.

# GUI controls

<p>
To enable/disable encryption in one-to-one conversation, press the lock button in the top right 
corner of the chat pane.
</p> 

<p>
To send your name to the conversation partner, press the button to the left of the lock (with "anonymous" icon).
</p> 

<p>
To attach an image, press an "attach" button in the bottom left corner of the chat pane (with "pin" icon). 
</p> 

<p>
To create a group conversation, press "+" button in the "conversations" pane's header. 
When the group conversation is selected, further members can be added to it by
pressing "+" button in the top right corner of the chat pane.
</p> 
