package com.doppelganger113.commandrunner;

import com.doppelganger113.commandrunner.sftp.SftpSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("ftp")
public class SFtpController {

    Logger logger = LoggerFactory.getLogger(SFtpController.class);

    private final SftpSessionService sftpService;
    private final CsvService csvService;

    public SFtpController(SftpSessionService sftpService, CsvService csvService) {
        this.sftpService = sftpService;
        this.csvService = csvService;
    }

    @GetMapping
    public List<String> ftp() throws IOException {
//        var file = new ClassPathResource("hw_200.csv").getInputStream();
//        this.csvService.readFile(new InputStreamReader(file)).forEach(System.out::println);

        return this.sftpService.getFiles("upload");
    }
}
