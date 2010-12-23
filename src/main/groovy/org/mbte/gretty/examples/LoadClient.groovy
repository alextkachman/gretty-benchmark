/*
 * Copyright 2009-2010 MBTE Sweden AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@Typed package org.mbte.gretty.examples

import java.util.concurrent.CountDownLatch
import org.mbte.gretty.httpclient.HttpClientPool
import org.mbte.gretty.httpclient.GrettyClient
import java.util.concurrent.atomic.AtomicInteger
import org.jboss.netty.handler.codec.http.HttpHeaders
import org.jboss.netty.handler.codec.http.HttpMethod
import org.jboss.netty.handler.codec.http.HttpVersion
import org.mbte.gretty.httpserver.GrettyHttpRequest
import groovypp.concurrent.ResourcePool
import org.jboss.netty.handler.codec.http.HttpResponseStatus

def clientsNumber = 5000

def iterationPerClient = 10

def totalIterations = clientsNumber * iterationPerClient

def cdl = new CountDownLatch(totalIterations)

HttpClientPool load = [
    remoteAddress:new InetSocketAddress("my-load-balancer-680767449.us-east-1.elb.amazonaws.com", 80),

    maxClientsConnectingConcurrently: 100,

    clientsNumber: clientsNumber,

    isResourceAlive: { resource ->
        ((GrettyClient)resource).isConnected ()
    }

]

def printStat = { String reason ->
    synchronized(cdl) { // does not really matter on what to sync
      println "$reason: $load.connectingClients $load.connectedClients"
    }
}

def jobCount = new AtomicInteger()

def startTime = System.currentTimeMillis()
for(i in 0..<totalIterations) {
    load.allocateResource { grettyClient ->
        def operation = this

        Thread.currentThread().sleep(10)

        GrettyHttpRequest req = [HttpVersion.HTTP_1_0, HttpMethod.GET, "/ping?grsessionid=$i"]
        req.setHeader HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE
        try {
            grettyClient.request(req, load.executor) { responseBindLater ->
                try {
                    def response = responseBindLater.get()
                    if(response?.status != HttpResponseStatus.OK) {
                        printStat "C$i: response ${response?.status}"
                        load.allocateResource operation
                    }
                    else {
                        def ji = jobCount.incrementAndGet()
                        def time = System.currentTimeMillis() - startTime
                        if(ji % 500 == 0)
                        printStat "C$i: job completed ${ji} ${response.status} ${time.intdiv(ji)} ms/op $time"
                        cdl.countDown ()
                    }
                }
                catch(e) {
                    printStat "C$i: $e"
                    load.allocateResource operation
                }
                finally {
                    load.releaseResource(grettyClient)
                }
            }
        }
        catch(e) {
            load.releaseResource(grettyClient)
            load.allocateResource (ResourcePool.Allocate)operation
        }
    }
}

cdl.await()

println "COMPLETED"

//        assert load.connectedClients == clientsNumber
//        assert load.connectingClients == 0
//        assert load.ids == clientsNumber
//load.stop ()
