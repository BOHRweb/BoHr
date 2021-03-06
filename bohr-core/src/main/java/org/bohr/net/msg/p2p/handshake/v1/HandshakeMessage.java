/**
 * Copyright (c) 2019 The Bohr Developers
 *
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package org.bohr.net.msg.p2p.handshake.v1;

import java.util.ArrayList;
import java.util.List;

import org.bohr.Network;
import org.bohr.config.Config;
import org.bohr.crypto.Hex;
import org.bohr.crypto.Key;
import org.bohr.net.Peer;
import org.bohr.net.msg.Message;
import org.bohr.net.msg.MessageCode;
import org.bohr.util.SimpleDecoder;
import org.bohr.util.SimpleEncoder;
import org.bohr.util.TimeUtil;

public class HandshakeMessage extends Message {

    protected final Peer peer;
    protected final long timestamp;
    protected final Key.Signature signature;

    /**
     * Create a message instance.
     *
     * @param code
     * @param responseMessageClass
     */
    public HandshakeMessage(MessageCode code, Class<?> responseMessageClass,
            Network network, short networkVersion, String peerId, String ip, int port,
            String clientId, long latestBlockNumber, Key coinbase) {
        super(code, responseMessageClass);

        this.peer = new Peer(network, networkVersion, peerId, ip, port,
                clientId, mandatoryCapabilities(network),
                latestBlockNumber);
        this.timestamp = TimeUtil.currentTimeMillis();

        SimpleEncoder enc = new SimpleEncoder();
        enc.writeBytes(encodePeer(peer));
        enc.writeLong(timestamp);
        this.signature = coinbase.sign(enc.toBytes());
        enc.writeBytes(signature.toBytes());

        this.body = enc.toBytes();
    }

    public HandshakeMessage(MessageCode code, Class<?> responseMessageClass, byte[] body) {
        super(code, responseMessageClass);

        SimpleDecoder dec = new SimpleDecoder(body);
        this.peer = decodePeer(dec.readBytes());
        this.timestamp = dec.readLong();
        this.signature = Key.Signature.fromBytes(dec.readBytes());

        this.body = body;
    }

    /**
     * Validates this message.
     *
     * <p>
     * NOTE: only data format and signature is checked here.
     * </p>
     *
     * @param config
     * @return true if success, otherwise false
     */
    public boolean validate(Config config) {
        if (peer != null && validatePeer(peer)
                && Math.abs(TimeUtil.currentTimeMillis() - timestamp) <= config.netHandshakeExpiry()
                && signature != null
                && peer.getPeerId().equals(Hex.encode(signature.getAddress()))) {

            SimpleEncoder enc = new SimpleEncoder();
            enc.writeBytes(encodePeer(peer));
            enc.writeLong(timestamp);

            return Key.verify(enc.toBytes(), signature);
        } else {
            return false;
        }
    }

    public Peer getPeer() {
        return peer;
    }

    public long getTimestamp() {
        return timestamp;
    }

    private static String[] mandatoryCapabilities(Network network) {
        switch (network) {
        case MAINNET:
            return new String[] { "Bohr" };
        case TESTNET:
        case DEVNET:
        default:
            return new String[] { "Bohr_TESTNET" };
        }
    }

    private static boolean validatePeer(Peer peer) {
        return peer.getIp() != null && peer.getIp().length() <= 128
                && peer.getPort() >= 0
                && peer.getNetworkVersion() >= 0
                && peer.getClientId() != null && peer.getClientId().length() < 128
                && peer.getPeerId() != null && peer.getPeerId().length() == 40
                && peer.getLatestBlockNumber() >= 0
                && peer.getCapabilities() != null && peer.getCapabilities().length <= 128;
    }

    private static byte[] encodePeer(Peer peer) {
        SimpleEncoder enc = new SimpleEncoder();
        enc.writeString(peer.getIp());
        enc.writeInt(peer.getPort());
        enc.writeShort(peer.getNetworkVersion());
        enc.writeString(peer.getClientId());
        enc.writeString(peer.getPeerId());
        enc.writeLong(peer.getLatestBlockNumber());

        // encode capabilities
        enc.writeInt(peer.getCapabilities().length);
        for (String capability : peer.getCapabilities()) {
            enc.writeString(capability);
        }

        return enc.toBytes();
    }

    private static Peer decodePeer(byte[] bytes) {
        SimpleDecoder dec = new SimpleDecoder(bytes);
        String ip = dec.readString();
        int port = dec.readInt();
        short p2pVersion = dec.readShort();
        String clientId = dec.readString();
        String peerId = dec.readString();
        long latestBlockNumber = dec.readLong();

        // decode capabilities
        List<String> capabilities = new ArrayList<>();
        for (int i = 0, size = dec.readInt(); i < size; i++) {
            capabilities.add(dec.readString());
        }

        return new Peer(null, p2pVersion, peerId, ip, port,
                clientId, capabilities.toArray(new String[0]),
                latestBlockNumber);
    }
}
