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
import java.lang.ref.Reference
import java.util.concurrent.Executors

HttpClientPool load = [
    remoteAddress:new InetSocketAddress("my-load-balancer-680767449.us-east-1.elb.amazonaws.com", 80),
    localAddress:new InetSocketAddress("10.0.0.1", 0),

    maxClientsConnectingConcurrently: 1000,

    clientsNumber: 100
]

def iterationsPerClient = 10
def cdl = new CountDownLatch(load.clientsNumber*iterationsPerClient)

def printStat = { String reason ->
    synchronized(cdl) { // does not really matter on what to sync
      println "$reason: $load.connectingClients $load.connectedClients"
    }
}

def jobCount = new AtomicInteger()

def startTime = System.currentTimeMillis()

def requestExecutor = Executors.newFixedThreadPool(Runtime.runtime.availableProcessors() * 10)

for(i in 0..<load.clientsNumber) {
    AtomicInteger iterations = [iterationsPerClient]
    load.allocateResource { grettyClient ->
//        printStat ""
//        Thread.currentThread().sleep 1000
        ResourcePool.Allocate  withClient = this

        if(!grettyClient.connected) {
            load.releaseResource grettyClient
            load.allocateResource withClient
            return
        }

        def ownStart = System.currentTimeMillis()

        GrettyHttpRequest req = [HttpVersion.HTTP_1_0, HttpMethod.GET, "/ping?grsessionid=$i"]
        req.setHeader HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE
        try {
            grettyClient.request(req, requestExecutor) { responseBindLater ->
                try {
                    def response = responseBindLater.get()
                    if(response?.status != HttpResponseStatus.OK) {
                        printStat "C$i: response ${response?.status}"

                        requestExecutor.execute {
                            withClient(grettyClient)
                        }
                    }
                    else {
                        def ji = jobCount.incrementAndGet()
                        def millis = System.currentTimeMillis()
                        def time = millis - startTime
//                        if(ji % 50 == 0)
                        printStat "C$i: iteration ${iterations.get()} completed ${response.status} ${time.intdiv(ji)} ms/op $time ${millis-ownStart}"
                        cdl.countDown ()

                        if(iterations.decrementAndGet() > 0) {
                            requestExecutor.execute {
                                withClient(grettyClient)
                            }
                        }
                        else {
                            printStat "C$i: job completed"
                            // we don't disconnect channel here with purpose
                            // otherwise new one will appear in the pool
                            grettyClient.disconnect()
                        }
                    }
                }
                catch(e) {
                    printStat "C$i: $e"
                    // we need to retry with new client
                    load.releaseResource grettyClient
                    load.allocateResource withClient
                }
            }
        }
        catch(e) {
            // we need to retry with new client
            load.releaseResource grettyClient
            load.allocateResource withClient
        }
    }
}

cdl.await()
println "DONE"
