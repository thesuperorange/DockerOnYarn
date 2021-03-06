package org.nchc.yarnapp;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.protocolrecords.AllocateResponse;
import org.apache.hadoop.yarn.api.protocolrecords.RegisterApplicationMasterResponse;
import org.apache.hadoop.yarn.api.records.*;
import org.apache.hadoop.yarn.client.api.AMRMClient;
import org.apache.hadoop.yarn.client.api.AMRMClient.ContainerRequest;
import org.apache.hadoop.yarn.client.api.NMClient;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.util.Records;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

/**
 * Created by superorange on 10/23/15.
 */
public class MyApplicationMaster {
    private static String MysqlIp = "140.110.141.62";
    private static String MysqlUser = "root";
    private static String MysqlPwd = "1234";
    private static String MysqlDb = "ipManager";

    private static final Logger LOG = LoggerFactory.getLogger(MyApplicationMaster.class);

    public static void main(String[] args) throws Exception {

        LOG.info("Running ApplicationMaster");

        final String dockerCommand = "docker run --privileged=true -e HD_IP=";//+args[0]+" -i -t -d gnssh/pipwork:v2";//"docker run hello-world";
        String dockerImgName = " -i -t -d gnssh/pipwork:v2";
        final int numOfContainers = 3;//Integer.valueOf(args[1]);
        YarnConfiguration conf = new YarnConfiguration();
        // Point #2
        LOG.info("Initializing AMRMCLient");
        AMRMClient<ContainerRequest> rmClient = AMRMClient.createAMRMClient();
        rmClient.init(conf);
        rmClient.start();
        LOG.info("Initializing NMCLient");
        NMClient nmClient = NMClient.createNMClient();
        nmClient.init(conf);
        nmClient.start();
        // Point #3
        LOG.info("Register ApplicationMaster");

        rmClient.registerApplicationMaster(NetUtils.getHostname(), 0, "");
        // Point #4
        Priority priority = Records.newRecord(Priority.class);
        priority.setPriority(0);

        LOG.info("Setting Resource capability for Containers");
        Resource capability = Records.newRecord(Resource.class);
        capability.setMemory(128);
        capability.setVirtualCores(1);
        for (int i = 0; i < numOfContainers; ++i) {
            ContainerRequest containerRequested = new ContainerRequest(capability, null, null, priority, true);
            // Resource, nodes, racks, priority and relax locality flag
            rmClient.addContainerRequest(containerRequested);
        }
        // Point #6
        int allocatedContainers = 0;
        LOG.info("Requesting container allocation from ResourceManager");

        while (allocatedContainers < numOfContainers) {
            AllocateResponse response = rmClient.allocate(0);
            for (Container container : response.getAllocatedContainers()) {
                ++allocatedContainers;x

                //getip
                SimpleMysql MYSQL = new SimpleMysql(MysqlIp,MysqlUser,MysqlPwd,MysqlDb);

                String ip = MYSQL.getIP();
                if (ip != null && !ip.isEmpty()) {
                    System.out.println("getIP: " + ip);
                    MYSQL.setIPStatus(ip, "1");
                }
                else{
                    //System.out.println("ip=null");
                    break;
                }

                String pythonCmd = "python /tmp/startDocker.py -p "+ip+" -n "+container.getNodeId()+" -y "+container.getId();
                String shellCommand = dockerCommand+ ip+ dockerImgName;
                // Launch container by creating ContainerLaunchContext
                ContainerLaunchContext ctx = Records.newRecord(ContainerLaunchContext.class);

                ctx.setCommands(Collections.singletonList(pythonCmd + " 1>/tmp/stdout" + " 2>/tmp/stderr"));
                LOG.info("command: "+shellCommand + " 1>"
                        + ApplicationConstants.LOG_DIR_EXPANSION_VAR  + "/stdout" + " 2>"
                        + ApplicationConstants.LOG_DIR_EXPANSION_VAR  + "/stderr");
                LOG.info("Starting container on node : " + container.getNodeHttpAddress());
                nmClient.startContainer(container, ctx);
            }
            Thread.sleep(100);
        }

        // Point #6
        int completedContainers = 0;
        while (completedContainers < numOfContainers) {
            AllocateResponse response = rmClient.allocate(completedContainers / numOfContainers);
            for (ContainerStatus status : response.getCompletedContainersStatuses()) {
                ++completedContainers;
                LOG.info("Container completed : " + status.getContainerId());
                LOG.info("Completed container " + completedContainers);
            }
            Thread.sleep(100);
        }

        //force error to print log
        //   RegisterApplicationMasterResponse response1 = rmClient.registerApplicationMaster(NetUtils.getHostname(), 0, "");

        rmClient.unregisterApplicationMaster(FinalApplicationStatus.SUCCEEDED,"", "");
    }


}
