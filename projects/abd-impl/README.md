# ABD Protocol
> A simple version of the ABD protocol presented in the paper "Sharing Memory Robustly in Message-Passing Systems - Hagit Attiya, Amotz Bar-Noy, Danny Dolev"


Note: Only clients will read and write. The servers that host the registers will only store the register.

### Client:
`./client host1:port1,host2:port2,.... write register_name new_value`

Example:
```
java -cp target/abd-1.0-SNAPSHOT-jar-with-dependencies.jar edu.sjsu.cs249.abd.client.ABDClient 10.10.10.1:5556,10.10.10.10:9000,10.10.10.3:2221,10.10.10.7:8000,10.10.10.11:2222,10.10.10.17:2323,10.10.10.15:2699,10.10.10.21:9001 read regDemo
```

* The write command finishes by printing either "success" or "failure".
* The read command finishes by printing the value of the register with the version number in parenthesis or the message "failed".


### Server
* The server must be able to be stopped or killed and restarted without losing data that it has acknowledged.
