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

package org.mbte.gretty.examples

import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.auth.PropertiesCredentials
import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.DescribeInstancesResult

@Typed class Ec2Env {
    private PropertiesCredentials awsCredentials
    private AmazonEC2Client awsClient

    protected String myIp = InetAddress.localHost.hostAddress

    protected HashMap<String,Instance> myInstances = [:]

    Instance myInstance

    Ec2Env () {
        File credentialsFile = [System.getProperty('user.home') + '/.aws/credentials']
        if(!credentialsFile.exists() || !credentialsFile.canRead()) {
            credentialsFile = ['../.aws/credentials']
            if(!credentialsFile.exists() || !credentialsFile.canRead()) {
                throw new IOException(credentialsFile.absolutePath)
            }
        }

        awsCredentials = [credentialsFile]
        awsClient = [awsCredentials]

        def instances = awsClient.describeInstances()
        initCluster(instances)

        startClusterMonitoring()
    }

    protected void initCluster(DescribeInstancesResult instances) {
        for (r in instances.reservations) {
            for (i in r.instances) {
                if (myIp == i.privateIpAddress) {
                    myInstance = i
                    break
                }
            }
        }
    }

    protected void startClusterMonitoring () {
        Thread t = [
            run: {
                String myGroup
                for(;;) {
                    def instances = awsClient.describeInstances()

                    synchronized(this) {
                        Map<String,Instance> insts = [:]

                        Set<Instance> newi = []
                        for(r in instances.reservations) {
                            if(!myGroup || r.groupNames[0] == myGroup) {
                                for(i in r.instances) {
                                  if(myIp == i.privateIpAddress) {
                                      def old = myInstance
                                      myInstance = i
                                      onOwnInfoUpdate i, old
                                  }
                                  else {
                                      insts[i.instanceId] = i
                                      def old = myInstances[i.instanceId]
                                      myInstances[i.instanceId] = i
                                      if(!old) {
                                          newi << i
                                          onInstanceAppear i
                                      }
                                      else {
                                          onInstanceUpdate i, old
                                      }
                                  }
                                }
                            }
                        }

                        Set<String> gone = []
                        for(ii in myInstances.entrySet()) {
                            if(!insts.containsKey(ii.key)) {
                                gone << ii.key
                            }
                        }
                        for(g in gone) {
                            onInstanceDisappear myInstances.remove(g)
                        }
                    }
                    Thread.currentThread().sleep(15000)
                }
            }
        ]
        t.start()
    }

    static class Listener {
        protected void onOwnInfoUpdate(Instance currentInstance, Instance oldInstance) {
        }

        protected void onInstanceUpdate(Instance currentInstance, Instance oldInstance) {
        }

        protected void onInstanceAppear(Instance currentInstance) {
        }

        protected void onInstanceDisappear(Instance oldInstance) {
        }
    }

    private final List<Listener> listeners = []

    protected void onOwnInfoUpdate(Instance currentInstance, Instance oldInstance) {
        for(l in listeners)
            l.onOwnInfoUpdate currentInstance, oldInstance
    }

    protected void onInstanceUpdate(Instance currentInstance, Instance oldInstance) {
        for(l in listeners)
            l.onInstanceUpdate currentInstance, oldInstance
    }

    protected void onInstanceAppear(Instance currentInstance) {
        for(l in listeners)
            l.onInstanceAppear currentInstance
    }

    protected void onInstanceDisappear(Instance oldInstance) {
        for(l in listeners)
            l.onInstanceDisappear oldInstance
    }

    Map<String,Instance> getInstances () {
        synchronized(this) {
            return myInstances.clone()
        }
    }
}
