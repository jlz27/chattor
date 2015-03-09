# Chattor

### Set up

The following instructions have been tested on Ubuntu 14.04.

1. Install Tor via `sudo apt-get install tor`

2. Set up Tor hidden service by editing /etc/tor/torrc and add the following lines

  > HiddenServiceDir /home/username/chattor
  
  > HiddenServicePort 3000 127.0.0.1:3000

3. Change config to such that the `clientDir` matches the `HiddenServiceDir` from above,
`clientPort` should match `HiddenServicePort` and `secretKey` should point to an ASCII armored PGP secret key file.

4. To launch the application, run `java -jar ChatTor-0.0.1-SNAPSHOT.jar config` from the top level directory of this repository.
Currently, the keystores and keyserver_list are not packaged into the distribution jar.

### Usage

1. The application will first prompt for a username, the username will be used to retrieve the user's PGP public key therefore its value
must be unique such that the following query https://pgp.mit.edu/pks/lookup?op=get&search=jlzhao (where `jlzhao` is the username you provided) returns the public key matches the private key provided in the config file.

2. After the client successfully registers with the chat server, it will begin accepting new conversation commands. To start a new conversation, type
`/n jlzhao`. This will create a secure connection with the user and set your current conversation to be with `jlzhao`.

3. Typing any message and hitting enter will send that message to the current chat session.

4. To switch chat sessions, simply type `/n newUsername`. This will switch your conversation to be with `newUsername` and any new messages you type will be sent to that user.
