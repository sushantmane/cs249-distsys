Fault-Tolerant Simulation of Shared Memory in Distributed Networks
==================================================================

Implementation of simple version of the **ABD protocol** presented in the paper "Sharing Memory Robustly in Message-Passing Systems" by Hagit Attiya, Amotz Bar-Noy, and Danny Dolev

### Details
* Tolerates `f` stopping failures, requires `n > 2f`
* Single-Writer Multi-Reader (SWMR) objects.
* Implement atomic object for each shared variable x, then combine.
* No transactions, no synchronization.
---
### Implementation
 Only clients will read and write. The servers that host the registers will only store the register.

#### Client:
```bash
./client host1:port1,host2:port2,...,hostN,portN write register_name new_value
```

Example:
```bash
java -cp target/abd-1.0-SNAPSHOT-jar-with-dependencies.jar edu.sjsu.cs249.abd.client.ABDClient 10.10.10.1:5556,10.10.10.10:9000,10.10.10.3:2221,10.10.10.7:8000,10.10.10.11:2222,10.10.10.17:2323,10.10.10.15:2699,10.10.10.21:9001 read regDemo
```
* The write command finishes by printing either "success" or "failure".
* The read command finishes by printing the value of the register with the version number in parenthesis or the message "failed".

#### Server
* The server must be able to be stopped or killed and restarted without losing data that it has acknowledged.

---
### References
1. Hagit Attiya, Amotz Bar-Noy, and Danny Dolev, Sharing Memory Robustly in Message-Passing Systems
2. https://ocw.mit.edu/courses/electrical-engineering-and-computer-science/6-852j-distributed-algorithms-fall-2009/lecture-notes/MIT6_852JF09_lec23.pdf
