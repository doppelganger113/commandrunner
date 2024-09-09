package com.doppelganger113.commandrunner.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Vector;

@Service
public class SftpSessionService {
    private final SftpSession.Options options = new SftpSession.Options(
            "localhost",
            "foo",
            "pass",
            2222
    );

    Logger log = LoggerFactory.getLogger(SftpSessionService.class);

    public List<String> getFiles(String path) {
        try (SftpSession session = SftpSession.createSftpSession(options)) {
            log.info("Established SFTP connection");
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = session.channel.ls(path);

            return entries
                    .stream()
                    .filter(e -> !e.getAttrs().isDir() && !e.getAttrs().isLink())
                    .map(ChannelSftp.LsEntry::toString)
                    .toList();
        } catch (SftpException e) {
            throw new RuntimeException(e);
        }
    }
}
