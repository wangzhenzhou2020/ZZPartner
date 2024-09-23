package com.yupi.yupao;

import com.yupi.yupao.util.AlgorithmUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;

@SpringBootTest
class YupaoBackendDuplicateApplicationTests {

    @Test
    void contextLoads() {
        ArrayList<String> strings = new ArrayList<>();
        strings.add("a");
        strings.add("b");
        strings.add("c");

        ArrayList<String> strings2 = new ArrayList<>();
        strings2.add("d");
        strings2.add("c");
        strings2.add("b");
        strings2.add("a");

        System.out.println(AlgorithmUtils.minDistance(strings2,strings));
    }


}
