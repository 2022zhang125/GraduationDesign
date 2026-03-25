package com.example.ncfback;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@MapperScan("com.example.ncfback.mapper")
@ConfigurationPropertiesScan
public class NcfBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(NcfBackApplication.class, args);
    }
}
