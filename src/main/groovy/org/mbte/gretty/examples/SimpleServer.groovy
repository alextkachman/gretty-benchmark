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

Jedis jedis = ["localhost"]
jedis.connect()

GrettyServer server = [
    localAddress: new InetSocketAddress(InetAddress.localHost.hostName, 8080),

    webContexts: [
        "/ping" : [
            default: {
                synchronized(jedis) {
                    def var = request.parameters['grsessionid'][0]
                    jedis.set (var, "blah-blah-blah")
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
server.start ()

Thread t = [
        run: {
            for(;;) {
                Thread.currentThread().sleep(3000)
                println server.ioMonitor.bytesSent
            }
        }
]
t.start()

println server.localAddress
println 'Started...'
