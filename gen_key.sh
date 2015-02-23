type=JCEKS
server_pass=$1
client_pass=client
dir=keystore
server_key=$dir/key_server
client_trust=$dir/trust_client1
rsa_size=4096

rm -rf keystore
mkdir keystore

keytool -genkeypair -dname "cn=ChatTor Server, ou=Server, o=Directory Server, c=US" -alias server_key -keystore $server_key -storetype $type -keyalg RSA -keysize $rsa_size -storepass $server_pass -keypass $server_pass

keytool -exportcert -alias server_key -file server_pub.cer -keystore $server_key -storetype $type -storepass $server_pass -keypass $server_pass

keytool -importcert -noprompt -file server_pub.cer -alias server_key -keystore $client_trust -storepass $client_pass -keypass $client_pass -storetype $type

rm server_pub.cer
