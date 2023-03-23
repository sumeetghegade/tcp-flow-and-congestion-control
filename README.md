TCP with Flow and Congestion Control

This project implements a TCP client and server using sliding window protocol for flow control and congestion control. For simplicity, client only sends sequence numbers instead of actual packets to the server and server responds with ACK numbers. Client adjusts the size of sliding window based on received ACKs. 
Client probabilistically drops 1% of the packets. Server keeps track of the missing packets and client retransmits the dropped packets after a specific time.

Results:
Packets sent: 10,000,000
Max sequence number: 2^16

Client output:

Server output:

Calculated Goodput: 87.74


Window size over time:

Sequence number received over time:

Retransmission table:
