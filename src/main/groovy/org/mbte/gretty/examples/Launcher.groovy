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

import groovypp.channels.SupervisedChannel
import java.util.concurrent.Executors
import com.amazonaws.services.ec2.model.Instance
import org.mbte.gretty.examples.Ec2Env.OwnInstanceUpdate

class Launcher extends SupervisedChannel {

    private Instance myInstance
    private String   myRole
    private String   redisHost
    private Ec2Env   ec2

    private SupervisedChannel worker

    protected void doStartup() {
        super.doStartup()
        startupChild(ec2 = new Ec2Env())
    }

    protected void doOnMessage(Object message) {
        switch(message) {
            case Ec2Env.OwnInstanceUpdate:
                def role = message.current?.role ?: 'local server'
                println message.current?.role

                if(myRole != role) {
                    switch (role) {
                        case 'redis':
                            if(myRole) {
                                worker.shutdown {
                                    println 'Starting Redis...'
                                    Runtime.runtime.exec ("../redis-2.0.4/redis-server redis.conf")
                                    System.exit(0)
                                }
                            }
                            else {
                                println 'Starting Redis...'
                                Runtime.runtime.exec ("../redis-2.0.4/redis-server redis.conf")
                                System.exit(0)
                            }
                        break

                        case 'server':
                            println 'Starting Server...\nWaiting for Redis node'
                            // the rest will happen when we will find Redis
                        break

                        case 'local server':
                            println 'Starting Local Server...'
                            startupChild (worker = new Server(redisHost: InetAddress.localHost.hostName))
                            shutdownChild ec2
                        break

                        default:
                            return // ignore
                    }
                }
                myInstance = message.current
                myRole = role
                break

            case Ec2Env.ForeignInstanceUpdate:
                break

            default:
                super.doOnMessage(message)
        }
    }

    private static String getRole (Instance instance) {
        String role
        for(t in instance.tags) {
            if(t.key.equalsIgnoreCase("role")) {
                role = t.value
            }
        }

        role?.toLowerCase() ?: 'server'
    }

    private void startServer() {
    }

    public static void main(String[] args) {
        def pool = Executors.newCachedThreadPool()
        Launcher launcher = [executor: pool]
        launcher.startup()
    }
}