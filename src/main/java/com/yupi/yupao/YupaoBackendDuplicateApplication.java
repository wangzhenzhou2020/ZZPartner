package com.yupi.yupao;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.yupi.yupao.mapper")  // 扫描mybatis的mapper文件
@EnableScheduling   // 开启定时任务，配合@Scheduled
public class YupaoBackendDuplicateApplication {

    public static void main(String[] args) {
        SpringApplication.run(YupaoBackendDuplicateApplication.class, args);
    }

}
