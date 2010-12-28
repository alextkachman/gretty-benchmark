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

import org.mbte.gretty.httpserver.GrettyServer
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisException

JedisPool jedisPool = ["10.251.53.155", 6379]

Ec2Env ec2 = []
ec2.start()

GrettyServer server = [
    localAddress: new InetSocketAddress(InetAddress.localHost.hostName, 8080),

    webContexts: [
        "/admin" : [
                staticResources: '/',

                public: {
                    get('/restart') {
                        System.exit(0)
                    }
                    websocket("/ws",[
                        onMessage: { msg ->
                            switch(msg) {
                                case 'get_stat':
                                    socket.send "INIT_SCREEN"
                                break

                                default:
                                    socket.send "UNKNOWN COMMAND:\n$msg"
                                break
                            }
                        },
                    ])
                }
        ],

        "/ping" : [
            default: {
                def var = request.parameters['grsessionid']
                if(var) {
                    Jedis jedis
                    try {
                        jedis = jedisPool.getResource()
                        jedis.set (var[0], "blah-blah-blah")
                    }
                    catch(JedisException e) {
                        if(jedis)
                            jedisPool.returnBrokenResource jedis
                        e.printStackTrace()
                        throw e
                    }
                    jedisPool.returnResource jedis
                }

                response.html = """
<html>
    <head>
        <title>Ping page</title>
    </head>
    <body>
        Hello, World!
    </body>
</html>
                """
            }
        ]
    ]
]

addShutdownHook {
    println "Stopping..."
    server.allConnected.close()
    server.stop()
}


server.start ()

Thread t = [
        run: {
            for(;;) {
                Thread.currentThread().sleep(3000)
                println "${server.ioMonitor.bytesSent} ${server.allConnected.size()}"
            }
        }
]
t.start()

println server.localAddress
println 'Started...'
