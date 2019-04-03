# t4g-code-sample

#### Author: Moritz Meister
---

### Description:
The goal of this seminar was to gain experience in designing experiments that measure 
the performance of complex software systems, process and analyze the results, 
as well as build models that describe the behavior of the system.  

The goal of the project was 
1) to implement a multi-threaded application and benchmark it, 
2) to analyze the measurements and build a model of the system and 
3) to determine the bottleneck of the system in terms of performance.  

The project is based on a simple Word Count Client-Server application that has the goal 
of turning an input document into a series of tuples in the form <word, count>, 
corresponding to each distinct word in the document and its number of occurrences. 
In large machine learning pipelines, this operation is often used to 
determine the similarity of two documents or to classify them into different 
categories based on their content. In this project, however, we study this 
application without plugging it into a larger pipeline.  

For details on the performance of the system please see the pdf in the repository.

### Build:
For building the programs, please use the ANT build script provided.
In order to do so, clone the repository and ```cd``` into root of the repository, 
then execute:  

```ant clean dist```  

to compile and package the source. Assuming you have the ANT builder installed
on your machine.

### Running the application:
To run the application you need to start the WoCoServer and as many WoCoClients as
you wish.

1. WoCoServer:  
The command line interface of the server looks as follows:  

```<listenaddress> <listenport> <cleaning> <threadcount>```  

For example, to run a multi-threaded server with four threads and HTML-cleaning
of the documents, execute the following command in a terminal from the root folder 
of the repository:  

```java -jar jars/WoCoServer.jar localhost 3000 1 4```  

Press CTRL+C to kill the server.

2. WoCoClient:  
Open a new terminal window and ```cd``` into the root directory again. The command line 
interface of the client looks as follows:  

```<servername> <serverport> <documentsize(KiB)> <opcount(x1000)> [<seed>] [<number of clients>]```. 

For example, to run an experiment and send 10.000 documents of size 16KiB to our
previously started server, run:  

```java -jar jars/WoCoClient.jar localhost 3000 16 10```  

You can observe the throughput of the system in the client terminal window, as well
as the final response time percentiles over all documents.  
You can open several terminals and start as many clients as you want at the same time
to observe how the system behaves.

### Requirements:
Tested with:
- Apache Ant(TM) version 1.10.5
- Java SDK 1.8.0_181
