# TCP with Flow and Congestion Control
=====================================

This project implements a TCP client and server using sliding window protocol for flow control and congestion control. For simplicity, client only sends sequence numbers instead of actual packets to the server and server responds with ACK numbers. Client adjusts the size of sliding window based on received ACKs. 
Client probabilistically drops 1% of the packets. Server keeps track of the missing packets and client retransmits the dropped packets after a specific time.

**Results:**
Packets sent: 10,000,000  
Max sequence number: 2^16

**Calculated Goodput: 87.74**

**Client output:**
<img src="/images/client_output.jpg" alt="**client output image">



**Server output:**
<img src="/images/server_output.jpg" alt="**client output image">


**Window size over time:**
<img src="/images/window_size_over_time.jpg" alt="**client output image">


**Sequence number received over time:**
<img src="/images/sequence_number_received.jpg" alt="**client output image">


**Retransmission table:**
<img src="/images/retransmission_table.png" alt="**client output image">
