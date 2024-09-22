package com.yupi.yupao.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class MyBatisPlusTest {
    @Autowired
    private UserService userService;

    @Test
    void test(){
        //
        long j=0;
        for (long i = 0; i < 30000000000L; i++) {
            j=2*i;
        }


    }
}
