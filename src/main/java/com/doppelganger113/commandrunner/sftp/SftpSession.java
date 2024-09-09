package com.doppelganger113.commandrunner.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class SftpSession implements AutoCloseable {

    public static SftpSession createSftpSession(Options options) {
        SftpSession sftpSession = new SftpSession(options);
        sftpSession.connect();
        return sftpSession;
    }

    public record Options(String host, String username, String password, int port) {
    }

    private Session session;
    private final Options options;

    public ChannelSftp channel;

    public SftpSession(Options options) {
        this.options = options;
    }

    private Session createSession() {
        try {
            JSch jSch = new JSch();
            Session session = jSch.getSession(options.username, options.host, options.port);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setPassword(options.password);
            session.connect();
            return session;
        } catch (JSchException e) {
            throw new RuntimeException(e);
        }
    }

    private ChannelSftp createSftpChannelAndConnect(Session session) {
        try {
            ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
            return channel;
        } catch (JSchException e) {
            throw new RuntimeException(e);
        }
    }

    private void connect() {
        session = createSession();
        channel = createSftpChannelAndConnect(session);
    }


    @Override
    public void close() {
        if (channel != null) {
            channel.disconnect();
        }
        if (session != null) {
            session.disconnect();
        }
    }
}
