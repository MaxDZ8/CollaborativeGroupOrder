Enable line wrap.

This dir contains .proto files for the network and serialization systems.
In origin I wanted to go through JVM serialization but I need to use other languages in the future. It seems it's not portable even across different JVMs besides some.

JSON needs some work and XML even more. Google protocol buffers produce binaries in nature, take care of everything, interact with potentially future RPC systems and are portable across different languages.

So, here we go. Protocol buffers v3 javanano are used for both the wire protocol and storage.

The network protocol is divided in 'phases'. This is due to the program not sharing any user or device identification. Instead, devices are 'promoted' from one phase to the next and eventually back, some phases are even internal to device or specific to character-on-device.


        ╔═══════════════════════════════════════════════╗
        ║                                               ║
        ║  Network Protocol - Group assembling activity ║
        ║                                               ║
        ╚═══════════════════════════════════════════════╝
    

When a new group is formed, the initial phase is the **silent** phase.
When a device initially connects it's considered a **silent** device. It is in the **silent** phase. Note this duality, I will use the words and adjectives interchangeably.

The protocol regulates communication between the **group owner** or **server** device, used by a single user and various other client devices. The group owner is usually the 'Dungeon Master' and holds special privileges.

The various messages are assumed by peers to be correctly processed. If the server receives a malformed message from a client, it will disconnect the client. If the client receives a malformed message from the server it will disconnect itself.

┌----------------┐
|  Silent phase  |
└----------------┘

A device is considered **silent** immediately after TCP connection is estabilished. At the time being those devices have no way to be identified.
Besides normal TCP keepalive, no data is transferred by server in this phase by its own initiative. Communication is initiated by client.

The first message MUST be Hello. Any other device will cause the connection to be terminated. Server replies by sending a GroupInfo object indicating group is open. This object is guaranteed to be constructed before the TCP listen socket is even initiated.

After the server has sent GroupInfo, it sends a CharBudget message. This tells the client how many PeerMessage the server will consider from the client. The client needs to send one PeerMessage to end the silent phase by providing a way for the group owner user to identify the client device. The client can send as many message as it wants, as long as it wants but the server will just ignore them at a certain point and eventually kill the connection.

Once a client device provided a PeerMessage the server promotes it to the Identified phase.

┌-----------------┐
|  Talking phase  |
└-----------------┘
It is a temporary phase, after the user has provided a way to identify his/her device. Now the group owner can see the device and guess who's what on its UI and deciding who to allow in and who to kick.

The talking phase is ended by the server when it sends to the device a GroupFormed message. This happens when the group owner selects which devices to accept and taps the 'make group' button.

┌------------------┐
|  Grouping phase  |
└------------------┘
In this phase the devices are all supported 'cooperating' somehow as they have been selected by the end of the talking phase. Unrecognized devices have been kicked by the server and no new TCP connections are being accepted.
Group owner here waits for the players to generate their Playing Characters, which are the stable characters in the group.
The various devices will send a PlayingCharacterDefinition for each character. When there are enough characters the group owner will for me group by using the 'Assemble group' button. As a result, the various characters will receive a GroupReady message. This message terminates the assemebly group procedure.

Messages received by the server after it has sent a GroupReady message are discarded.


        ╔════════════════════════════════════════════════╗
        ║                                                ║
        ║  Network Protocol - Going adventuring activity ║
        ║                                                ║
        ╚════════════════════════════════════════════════╝
    

This starts as the group formation activity, the client connects and sends an Hello, the server replies with a GroupInfo indicating group is closed to new members (GroupInfo.forming must be false).
The only allowed message is then GroupKey. If the server identifies the key as invalid it terminates TCP connection. Otherwise, it'll
send three consecutive PlayingCharacterList messages.
The three messages identify playing characters usually managed by the peer client device, PCs already assigned to other devices and PCs not yet assigned to any.

Each device can ASK to move a PC assigned to him to unassigned by PlayingCharacterMoveRequest. The same message can be used to move an unassigned character to itself. This is considerably different from the 'talking' phase but it's about that.

When all playing characters are assigned the group owner will be given the chance to "go adventuring". This sends a "Adventuring" message.

From now on, the clients are expected to be completely silent. Not even messages will be allowed and everything will result in a TCP disconnect of the specific client. At a certain point in the future the server starts a new battle by sending the client a NewOrder message and initiating the battle phase. The server can sometimes ask for ManualRollRequests but this does not initialize a battle phase!

┌----------------┐
|  Battle phase  |
└----------------┘

This phase is initiated from the server by sending a NewOrder message. Clients do nothing besides showing the new order list and its progress. This is signaled by the server by sending the client RoundProgress messages. When such a message contains ID of a character being managed by the device, the character becomes 'active'.

The server can send multiple NewOrder messages for PC or NPC shuffling.

Another message the server will send is ActiveStatus which marks characters from being active or not and eventually gives an explanation.

The device managing an active character transitions (temporarily) to active phase.

Battle phase ends when the server sends a NewOrder message with empty list.

Players can message the group owner so PeerMessage and CharBudget can happen here.

┌----------------┐
|  Active phase  |
└----------------┘

Only a single character on a single device in the group can be active at the same time. This phase will likely be the one with the most future work. Most players will be given a few things to do here. For the time being stuff to happen is limited to:
1- character can end his/her turn by EndTurn
2- character can request server to set active state for another character by StatusRequest
3- character can shuffle its own position in the initiative order (I wait for character X to act) or to undetermined (I prepare to shoot an arrow as soon as condition X happens) by Reorder


