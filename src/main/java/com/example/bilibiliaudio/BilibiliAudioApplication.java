package com.example.bilibiliaudio;

import com.example.bilibiliaudio.config.MediaProperties;
import com.example.bilibiliaudio.config.TaskProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({MediaProperties.class, TaskProperties.class})
public class BilibiliAudioApplication {

    public static void main(String[] args) {
        SpringApplication.run(BilibiliAudioApplication.class, args);
    }
}
