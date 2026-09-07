package com.mychat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 应用入口。Mapper 扫描骨架包，并递归覆盖 {@code com.mychat.apps.<id>.mapper}。 */
@SpringBootApplication
@MapperScan(
        basePackages = {"com.mychat.mapper", "com.mychat.apps"},
        annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
