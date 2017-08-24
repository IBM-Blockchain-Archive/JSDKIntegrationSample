package org.cr22rc;
/*
 *
 *  Copyright 2017 IBM - All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.commons.codec.binary.Hex;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.BlockInfo;
import org.hyperledger.fabric.sdk.BlockchainInfo;
import org.hyperledger.fabric.sdk.ChaincodeEvent;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.EventHub;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.InstallProposalRequest;
import org.hyperledger.fabric.sdk.InstantiateProposalRequest;
import org.hyperledger.fabric.sdk.Orderer;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.QueryByChaincodeRequest;
import org.hyperledger.fabric.sdk.SDKUtils;
import org.hyperledger.fabric.sdk.TransactionInfo;
import org.hyperledger.fabric.sdk.TransactionProposalRequest;
import org.hyperledger.fabric.sdk.TxReadWriteSetInfo;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.exception.TransactionEventException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.HFCAInfo;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

public class BMXHyperledgerFabricJSDKIntegrationSample {

    public static final String NETWORK_CONFIG_PEERORG_CA = "fabric-ca-peerorg1-23033a"; //Only test with this CA

    public static final String NETWORK_CONFIG_PEERORG = "PeerOrg1"; //Only test with this peer org

    public static final String TEST_CHANNEL = "ricks-test-channel";

    static final SampleStore SAMPLE_STORE = new SampleStore(new File("bmxBlockChainSampleStore.properties"));

    public static final String NETWORK_CONFIG_FILE = "bmxServiceCredentials.json";


    public static final String PEER_ADMIN_NAME = "admin";

    private static final int TRANSACTION_WAIT_TIME = 60000 * 6;
    private static final int DEPLOY_WAIT_TIME = 60000 * 5;
    private static final String EXPECTED_EVENT_NAME = "event";
    private static final String CHAIN_CODE_NAME = "example_cc_go";
    private static final String CHAIN_CODE_PATH = "github.com/example_cc";
    private static final String CHAIN_CODE_VERSION = "1";
    private static final String TEST_FIXTURES_PATH = "src/test/fixture";
    private static final long PROPOSAL_WAIT_TIME = 60000 * 5;
    private static final byte[] EXPECTED_EVENT_DATA = "!".getBytes(UTF_8);

    public static void main(String[] args) throws Exception {
        System.setProperty("org.hyperledger.fabric.sdk.channel.genesisblock_wait_time", 60000 * 15 + "");
        new BMXHyperledgerFabricJSDKIntegrationSample().run(args);



    }

    String testTxID = null;

    private void run(String[] args) throws Exception {

        NetworkConfig networkConfig = parseNetworkConfigFile(new File(NETWORK_CONFIG_FILE));

        SampleUser admin;

        if (SAMPLE_STORE.hasMember(PEER_ADMIN_NAME, NETWORK_CONFIG_PEERORG)) {
            admin = SAMPLE_STORE.getMember(PEER_ADMIN_NAME, NETWORK_CONFIG_PEERORG);
        } else {
            NetworkConfig.OrganizationConfig peerOrg1 = networkConfig.getOrganization(NETWORK_CONFIG_PEERORG);
            NetworkConfig.OrganizationConfig.CertificateAuthorityConfig certificateAuthority = peerOrg1.getCertificateAuthority(NETWORK_CONFIG_PEERORG_CA);

            NetworkConfig.NetworkConfigUser networkConfigRegistrar = certificateAuthority.getRegistrar(PEER_ADMIN_NAME);
            admin = SAMPLE_STORE.getMember(PEER_ADMIN_NAME, NETWORK_CONFIG_PEERORG);
            admin.setEnrollmentSecret(networkConfigRegistrar.getEnrollSecret());
            admin.setAffiliation(networkConfigRegistrar.getAffiliation());
            admin.setMspId(networkConfigRegistrar.getMspId());

        }

        assert admin != null;

        if (!admin.isEnrolled()) {
            HFCAClient hfcaClient = constructFabricCAEndpoint(networkConfig);

            Enrollment enrollment = hfcaClient.enroll(admin.getName(), admin.getEnrollmentSecret());
            admin.setEnrollment(enrollment);
            User.userContextCheck(admin);

            out("Peer Admin %s not previously enrolled. Make sure all peers include certificate.", admin.getName());
            printUser(admin);
            System.exit(8);
        }

        HFClient client = HFClient.createNewInstance();
        client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        client.setUserContext(admin);

        Channel testChannel = constructChannelFromNetworkConfig(client, networkConfig, TEST_CHANNEL);
        runChannel(client, testChannel, true, 10);

    }

    private Channel constructChannelFromNetworkConfig(HFClient client, NetworkConfig networkConfig, String channelName) throws Exception {

        Map<String, Orderer> orderers = new HashMap<>();
        Map<String, Peer> peers = new HashMap<>();
        Map<String, EventHub> eventHubs = new HashMap<>();

        constructFabricEndpointsNetworkConfig(client, networkConfig, orderers, peers, eventHubs);
        NetworkConfig.ChannelConfig networkConfigChannel = networkConfig.getChannel(channelName);

        //If channel information exists in the configuration then just filter it out otherwise just assume all;
        if (null != networkConfigChannel) {

            HashMap<String, Orderer> channelOrders = new HashMap<>(orderers); //copy
            for (String name : orderers.keySet()) {
                if (null == networkConfigChannel.getOrderer(name)) {
                    channelOrders.remove(name);
                }
            }
            orderers = channelOrders;

            HashMap<String, Peer> channelPeers = new HashMap<>(peers); //copies..
            HashMap<String, EventHub> channelEventHubs = new HashMap<>(eventHubs);

            for (String name : peers.keySet()) {
                if (null == networkConfigChannel.getPeer(name)) {
                    channelPeers.remove(name);
                    channelEventHubs.remove(name);
                }
            }
            peers = channelPeers;
            eventHubs = channelEventHubs;
        }

        assert !orderers.isEmpty() : "No Orderers were found in network configuration file!";
        assert !peers.isEmpty() : "No Peers were found in network configuration file!";
        //     assert !eventHubs.isEmpty() : "No Event hubs were found in network configuration file!";

        Channel channel = client.newChannel(channelName);

        for (Orderer orderer : orderers.values()) {
            channel.addOrderer(orderer);
        }

        for (EventHub eventHub : eventHubs.values()) {
            channel.addEventHub(eventHub);
        }

        for (Peer peer : peers.values()) {

            Set<String> channels = client.queryChannels(peer);
            if (!channels.contains(channelName)) {

                out("Need to join peer %s to channel %s", peer.getName(), channelName);
                channel.joinPeer(peer);

            } else {
                channel.addPeer(peer);
            }

        }

        channel.initialize();
        return channel;

    }

    //CHECKSTYLE.OFF: Method length is 320 lines (max allowed is 150).
    void runChannel(HFClient client, Channel channel, boolean installChaincode, int delta) {

        class ChaincodeEventCapture { //A test class to capture chaincode events
            final String handle;
            final BlockEvent blockEvent;
            final ChaincodeEvent chaincodeEvent;

            ChaincodeEventCapture(String handle, BlockEvent blockEvent, ChaincodeEvent chaincodeEvent) {
                this.handle = handle;
                this.blockEvent = blockEvent;
                this.chaincodeEvent = chaincodeEvent;
            }
        }
        Vector<ChaincodeEventCapture> chaincodeEvents = new Vector<>(); // Test list to capture chaincode events.

        try {

            final String channelName = channel.getName();
            boolean isFooChain = true;
            out("Running channel %s", channelName);
            channel.setTransactionWaitTime(TRANSACTION_WAIT_TIME);
            channel.setDeployWaitTime(DEPLOY_WAIT_TIME);

            Collection<Orderer> orderers = channel.getOrderers();
            final ChaincodeID chaincodeID;
            Collection<ProposalResponse> responses;
            Collection<ProposalResponse> successful = new LinkedList<>();
            Collection<ProposalResponse> failed = new LinkedList<>();

            // Register a chaincode event listener that will trigger for any chaincode id and only for EXPECTED_EVENT_NAME event.

            String chaincodeEventListenerHandle = channel.registerChaincodeEventListener(Pattern.compile(".*"),
                    Pattern.compile(Pattern.quote(EXPECTED_EVENT_NAME)),
                    (handle, blockEvent, chaincodeEvent) -> {

                        chaincodeEvents.add(new ChaincodeEventCapture(handle, blockEvent, chaincodeEvent));

                        out("RECEIVED Chaincode event with handle: %s, chhaincode Id: %s, chaincode event name: %s, "
                                        + "transaction id: %s, event payload: \"%s\", from eventhub: %s",
                                handle, chaincodeEvent.getChaincodeId(),
                                chaincodeEvent.getEventName(), chaincodeEvent.getTxId(),
                                new String(chaincodeEvent.getPayload()), blockEvent.getEventHub().toString());

                    });

            //For non foo channel unregister event listener to test events are not called.
            if (!isFooChain) {
                channel.unRegisterChaincodeEventListener(chaincodeEventListenerHandle);
                chaincodeEventListenerHandle = null;

            }

            final String chaincodeName = CHAIN_CODE_NAME + "_" + System.currentTimeMillis();

            chaincodeID = ChaincodeID.newBuilder().setName(chaincodeName)
                    .setVersion(CHAIN_CODE_VERSION)
                    .setPath(CHAIN_CODE_PATH).build();

            if (installChaincode) {
                ////////////////////////////
                // Install Proposal Request
                //

                out("Creating install proposal");

                InstallProposalRequest installProposalRequest = client.newInstallProposalRequest();
                installProposalRequest.setChaincodeID(chaincodeID);

                installProposalRequest.setChaincodeSourceLocation(new File("chaincode/gocc/sample1"));

                installProposalRequest.setChaincodeVersion(CHAIN_CODE_VERSION);

                out("Sending install proposal");

                ////////////////////////////
                // only a client from the same org as the peer can issue an install request
                int numInstallProposal = 0;
                //    Set<String> orgs = orgPeers.keySet();
                //   for (SampleOrg org : testSampleOrgs) {

                Collection<Peer> peersFromOrg = channel.getPeers();
                numInstallProposal = numInstallProposal + peersFromOrg.size();
                responses = client.sendInstallProposal(installProposalRequest, peersFromOrg);

                for (ProposalResponse response : responses) {
                    if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                        out("Successful install proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
                        successful.add(response);
                    } else {
                        failed.add(response);
                    }
                }

                out("Received %d install proposal responses. Successful+verified: %d . Failed: %d", numInstallProposal, successful.size(), failed.size());

                if (failed.size() > 0) {
                    ProposalResponse first = failed.iterator().next();
                    fail("Not enough endorsers for install :" + successful.size() + ".  " + first.getMessage());
                }
            }

            ///////////////
            //// Instantiate chaincode.
            InstantiateProposalRequest instantiateProposalRequest = client.newInstantiationProposalRequest();
            instantiateProposalRequest.setProposalWaitTime(PROPOSAL_WAIT_TIME);
            instantiateProposalRequest.setChaincodeID(chaincodeID);
            instantiateProposalRequest.setFcn("init");
            instantiateProposalRequest.setArgs(new String[] {"a", "500", "b", "" + (200 + delta)});
            Map<String, byte[]> tm = new HashMap<>();
            tm.put("HyperLedgerFabric", "InstantiateProposalRequest:JavaSDK".getBytes(UTF_8));
            tm.put("method", "InstantiateProposalRequest".getBytes(UTF_8));
            instantiateProposalRequest.setTransientMap(tm);

            out("Sending instantiateProposalRequest to all peers with arguments: a and b set to 100 and %s respectively", "" + (200 + delta));
            successful.clear();
            failed.clear();

            responses = channel.sendInstantiationProposal(instantiateProposalRequest);

            for (ProposalResponse response : responses) {
                if (response.isVerified() && response.getStatus() == ProposalResponse.Status.SUCCESS) {
                    successful.add(response);
                    out("Succesful instantiate proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
                } else {
                    failed.add(response);
                }
            }
            out("Received %d instantiate proposal responses. Successful+verified: %d . Failed: %d", responses.size(), successful.size(), failed.size());
            if (failed.size() > 0) {
                ProposalResponse first = failed.iterator().next();
                fail("Not enough endorsers for instantiate :" + successful.size() + "endorser failed with " + first.getMessage() + ". Was verified:" + first.isVerified());
            }

            ///////////////
            /// Send instantiate transaction to orderer
            out("Sending instantiateTransaction to orderer with a and b set to 100 and %s respectively", "" + (200 + delta));
            channel.sendTransaction(successful, orderers).thenApply(transactionEvent -> {

                assert (transactionEvent.isValid()); // must be valid to be here.
                out("Finished instantiate transaction with transaction id %s", transactionEvent.getTransactionID());

                try {
                    successful.clear();
                    failed.clear();

                    ///////////////
                    /// Send transaction proposal to all peers
                    TransactionProposalRequest transactionProposalRequest = client.newTransactionProposalRequest();
                    transactionProposalRequest.setChaincodeID(chaincodeID);
                    transactionProposalRequest.setFcn("invoke");
                    transactionProposalRequest.setProposalWaitTime(PROPOSAL_WAIT_TIME);
                    transactionProposalRequest.setArgs(new String[] {"move", "a", "b", "100"});

                    Map<String, byte[]> tm2 = new HashMap<>();
                    tm2.put("HyperLedgerFabric", "TransactionProposalRequest:JavaSDK".getBytes(UTF_8)); //Just some extra junk in transient map
                    tm2.put("method", "TransactionProposalRequest".getBytes(UTF_8)); // ditto
                    tm2.put("result", ":)".getBytes(UTF_8));  // This should be returned see chaincode why.
                    tm2.put(EXPECTED_EVENT_NAME, EXPECTED_EVENT_DATA);  //This should trigger an event see chaincode why.

                    transactionProposalRequest.setTransientMap(tm2);

                    out("sending transactionProposal to all peers with arguments: move(a,b,100)");

                    Collection<ProposalResponse> transactionPropResp = channel.sendTransactionProposal(transactionProposalRequest, channel.getPeers());
                    for (ProposalResponse response : transactionPropResp) {
                        if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                            out("Successful transaction proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
                            successful.add(response);
                        } else {
                            failed.add(response);
                        }
                    }

                    // Check that all the proposals are consistent with each other. We should have only one set
                    // where all the proposals above are consistent.
                    Collection<Set<ProposalResponse>> proposalConsistencySets = SDKUtils.getProposalConsistencySets(transactionPropResp);
                    if (proposalConsistencySets.size() != 1) {
                        fail(format("Expected only one set of consistent proposal responses but got %d", proposalConsistencySets.size()));
                    }

                    out("Received %d transaction proposal responses. Successful+verified: %d . Failed: %d",
                            transactionPropResp.size(), successful.size(), failed.size());
                    if (failed.size() > 0) {
                        ProposalResponse firstTransactionProposalResponse = failed.iterator().next();
                        fail("Not enough endorsers for invoke(move a,b,100):" + failed.size() + " endorser error: " +
                                firstTransactionProposalResponse.getMessage() +
                                ". Was verified: " + firstTransactionProposalResponse.isVerified());
                    }
                    out("Successfully received transaction proposal responses.");

                    ProposalResponse resp = transactionPropResp.iterator().next();
                    byte[] x = resp.getChaincodeActionResponsePayload(); // This is the data returned by the chaincode.
                    String resultAsString = null;
                    if (x != null) {
                        resultAsString = new String(x, "UTF-8");
                    }
                    assert (":)".equals(resultAsString));

                    assert 200 == resp.getChaincodeActionResponseStatus(); //Chaincode's status.

                    TxReadWriteSetInfo readWriteSetInfo = resp.getChaincodeActionResponseReadWriteSetInfo();
                    //See blockwalker below how to transverse this
                    assert null != readWriteSetInfo;
                    assert (readWriteSetInfo.getNsRwsetCount() > 0);

                    ChaincodeID cid = resp.getChaincodeID();

                    ////////////////////////////
                    // Send Transaction Transaction to orderer
                    out("Sending chaincode transaction(move a,b,100) to orderer.");
                    return channel.sendTransaction(successful).get(TRANSACTION_WAIT_TIME, TimeUnit.SECONDS);

                } catch (Exception e) {
                    out("Caught an exception while invoking chaincode");
                    e.printStackTrace();
                    fail("Failed invoking chaincode with error : " + e.getMessage());
                }

                return null;

            }).thenApply(transactionEvent -> {
                try {

                    assert (transactionEvent.isValid()); // must be valid to be here.
                    out("Finished transaction with transaction id %s", transactionEvent.getTransactionID());
                    testTxID = transactionEvent.getTransactionID(); // used in the channel queries later

                    ////////////////////////////
                    // Send Query Proposal to all peers
                    //
                    String expect = "" + (300 + delta);
                    out("Now query chaincode for the value of b.");
                    QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();
                    queryByChaincodeRequest.setArgs(new String[] {"query", "b"});
                    queryByChaincodeRequest.setFcn("invoke");
                    queryByChaincodeRequest.setChaincodeID(chaincodeID);

                    Map<String, byte[]> tm2 = new HashMap<>();
                    tm2.put("HyperLedgerFabric", "QueryByChaincodeRequest:JavaSDK".getBytes(UTF_8));
                    tm2.put("method", "QueryByChaincodeRequest".getBytes(UTF_8));
                    queryByChaincodeRequest.setTransientMap(tm2);

                    Collection<ProposalResponse> queryProposals = channel.queryByChaincode(queryByChaincodeRequest, channel.getPeers());
                    for (ProposalResponse proposalResponse : queryProposals) {
                        if (!proposalResponse.isVerified() || proposalResponse.getStatus() != ProposalResponse.Status.SUCCESS) {
                            fail("Failed query proposal from peer " + proposalResponse.getPeer().getName() + " status: " + proposalResponse.getStatus() +
                                    ". Messages: " + proposalResponse.getMessage()
                                    + ". Was verified : " + proposalResponse.isVerified());
                        } else {
                            String payload = proposalResponse.getProposalResponse().getResponse().getPayload().toStringUtf8();
                            out("Query payload of b from peer %s returned %s", proposalResponse.getPeer().getName(), payload);
                            assert expect.equals(payload);
                        }
                    }

                    return null;
                } catch (Exception e) {
                    out("Caught exception while running query");
                    e.printStackTrace();
                    fail("Failed during chaincode query with error : " + e.getMessage());
                }

                return null;
            }).exceptionally(e -> {
                if (e instanceof TransactionEventException) {
                    BlockEvent.TransactionEvent te = ((TransactionEventException) e).getTransactionEvent();
                    if (te != null) {
                        fail(format("Transaction with txid %s failed. %s", te.getTransactionID(), e.getMessage()));
                    }
                }
                fail(format("Test failed with %s exception %s", e.getClass().getName(), e.getMessage()));

                return null;
            }).get(TRANSACTION_WAIT_TIME, TimeUnit.SECONDS);

            // Channel queries

            // We can only send channel queries to peers that are in the same org as the SDK user context
            // Get the peers from the current org being used and pick one randomly to send the queries to.
            Collection<Peer> peerSet = channel.getPeers();
            //  Peer queryPeer = peerSet.iterator().next();
            //   out("Using peer %s for channel queries", queryPeer.getName());

            BlockchainInfo channelInfo = channel.queryBlockchainInfo();
            out("Channel info for : " + channelName);
            out("Channel height: " + channelInfo.getHeight());
            String chainCurrentHash = Hex.encodeHexString(channelInfo.getCurrentBlockHash());
            String chainPreviousHash = Hex.encodeHexString(channelInfo.getPreviousBlockHash());
            out("Chain current block hash: " + chainCurrentHash);
            out("Chainl previous block hash: " + chainPreviousHash);

            // Query by block number. Should return latest block, i.e. block number 2
            BlockInfo returnedBlock = channel.queryBlockByNumber(channelInfo.getHeight() - 1);
            String previousHash = Hex.encodeHexString(returnedBlock.getPreviousHash());
            out("queryBlockByNumber returned correct block with blockNumber " + returnedBlock.getBlockNumber()
                    + " \n previous_hash " + previousHash);
            //          assertEquals(channelInfo.getHeight() - 1, returnedBlock.getBlockNumber());
            //          assertEquals(chainPreviousHash, previousHash);

            // Query by block hash. Using latest block's previous hash so should return block number 1
            byte[] hashQuery = returnedBlock.getPreviousHash();
            returnedBlock = channel.queryBlockByHash(hashQuery);
            out("queryBlockByHash returned block with blockNumber " + returnedBlock.getBlockNumber());
            //           assertEquals(channelInfo.getHeight() - 2, returnedBlock.getBlockNumber());

            // Query block by TxID. Since it's the last TxID, should be block 2
            returnedBlock = channel.queryBlockByTransactionID(testTxID);
            out("queryBlockByTxID returned block with blockNumber " + returnedBlock.getBlockNumber());
            //         assertEquals(channelInfo.getHeight() - 1, returnedBlock.getBlockNumber());

            // query transaction by ID
            TransactionInfo txInfo = channel.queryTransactionByID(testTxID);
            out("QueryTransactionByID returned TransactionInfo: txID " + txInfo.getTransactionID()
                    + "\n     validation code " + txInfo.getValidationCode().getNumber());

            if (chaincodeEventListenerHandle != null) {

                channel.unRegisterChaincodeEventListener(chaincodeEventListenerHandle);
                //Should be two. One event in chaincode and two notification for each of the two event hubs

                final int numberEventHubs = channel.getEventHubs().size();
                //just make sure we get the notifications.
                for (int i = 15; i > 0; --i) {
                    if (chaincodeEvents.size() == numberEventHubs) {
                        break;
                    } else {
                        Thread.sleep(90); // wait for the events.
                    }

                }
                assert (numberEventHubs == chaincodeEvents.size());

                for (ChaincodeEventCapture chaincodeEventCapture : chaincodeEvents) {
//                    assertEquals(chaincodeEventListenerHandle, chaincodeEventCapture.handle);
//                    assertEquals(testTxID, chaincodeEventCapture.chaincodeEvent.getTxId());
//                    assertEquals(EXPECTED_EVENT_NAME, chaincodeEventCapture.chaincodeEvent.getEventName());
//                    assertTrue(Arrays.equals(EXPECTED_EVENT_DATA, chaincodeEventCapture.chaincodeEvent.getPayload()));
//                    assertEquals(chaincodeName, chaincodeEventCapture.chaincodeEvent.getChaincodeId());

                    BlockEvent blockEvent = chaincodeEventCapture.blockEvent;
                    assert blockEvent != null : "chaincodeEventCapture.blockEvent is null!";
                    assert channelName.equals(blockEvent.getChannelId()) : format("Expected channel name %s, but got %s", channelName, blockEvent.getChannelId());
                    assert channel.getEventHubs().contains(blockEvent.getEventHub()) : "Event hub is not part of channel!";

                }

            }

            out("Running for Channel %s done", channelName);

        } catch (Exception e) {
            out("Caught an exception running channel %s", channel.getName());
            e.printStackTrace();
            fail("Test failed with error : " + e.getMessage());
        }
    }

    private void fail(String s) {
        throw new RuntimeException(s);
    }
    //CHECKSTYLE.ON: Method length is 320 lines (max allowed is 150).

    private void printUser(User user) {
        out("User: %s, MSPID: %s\nEnrollment certificate:\n%s", user.getName(), user.getMspId(), user.getEnrollment().getCert());
    }

    private void constructFabricEndpointsNetworkConfig(HFClient client, NetworkConfig networkConfig,
                                                       Map<String, Orderer> orderers,
                                                       Map<String, Peer> peers, Map<String, EventHub> eventHubs) throws Exception {

        for (NetworkConfig.OrdererConfig ordererConfig : networkConfig.getOrderers().values()) {

            Properties properties = ordererConfig.getProperties();
            properties.setProperty("sslProvider", "openSSL");
            properties.setProperty("negotiationType", "TLS");

            orderers.put(ordererConfig.getName(), client.newOrderer(ordererConfig.getName(), ordererConfig.getURL(),
                    properties));
        }

        for (NetworkConfig.OrganizationConfig.PeerConfig peerConfig : networkConfig.getOrganization(NETWORK_CONFIG_PEERORG).getPeers().values()) {

            Properties properties = peerConfig.getProperties();
            properties.setProperty("sslProvider", "openSSL");
            properties.setProperty("negotiationType", "TLS");

            peers.put(peerConfig.getName(), client.newPeer(peerConfig.getName(), peerConfig.getURL(),
                    properties));

            if (peerConfig.getEventURL() != null || !peerConfig.getEventURL().isEmpty()) {
                EventHub eventHub = client.newEventHub(peerConfig.getName(), peerConfig.getEventURL(), properties);
                eventHubs.put(eventHub.getName(), eventHub);

            }
        }

    }

    private HFCAClient constructFabricCAEndpoint(NetworkConfig networkConfig) throws Exception {

        //Get a fabric ca for the network.

        NetworkConfig.OrganizationConfig peerOrg1 = networkConfig.getOrganization(NETWORK_CONFIG_PEERORG);

        NetworkConfig.OrganizationConfig.CertificateAuthorityConfig certificateAuthority = peerOrg1.getCertificateAuthority(NETWORK_CONFIG_PEERORG_CA);

        Properties properties = new Properties();
        properties.put("pemBytes", certificateAuthority.getTLSCerts().getBytes());
        properties.setProperty("allowAllHostNames", "true");
        HFCAClient hfcaClient = HFCAClient.createNewInstance(certificateAuthority.getCAName(),
                certificateAuthority.getURL(), properties);
        hfcaClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());

        HFCAInfo info = hfcaClient.info(); //basic check if it connects.
        assert info != null : "hfcaClient.info() is null";
        return hfcaClient;

    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                    Network configuration

    private static NetworkConfig parseNetworkConfigFile(File parseFilie) throws FileNotFoundException {

        JsonReader reader = Json.createReader(new BufferedReader(new FileReader(parseFilie)));
        JsonObject jobj = (JsonObject) reader.read();
        NetworkConfig networkConfig = new NetworkConfig(jobj);

        return networkConfig;

    }

    static void out(String format, Object... args) {

        System.err.flush();
        System.out.flush();

        System.out.println(format(format, args));
        System.err.flush();
        System.out.flush();

    }

}