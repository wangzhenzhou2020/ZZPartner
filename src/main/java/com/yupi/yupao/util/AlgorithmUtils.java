package com.yupi.yupao.util;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 算法工具类
 */
public class AlgorithmUtils {

    /**
     * 编辑距离算法（用于计算最相似的两组标签）
     * 原理：https://blog.csdn.net/DBC_121/article/details/104198838
     * by zz: 这里不应该用编辑举例，因为这里的参数只是一组标签，虽然用List装着，但实际上这些标签业务上之间没有顺序关系！
     *        因此，修改算法为set的并集-set交集，即是距离。
     * @param tagList1
     * @param tagList2
     * @return
     */
    public static int minDistance(List<String> tagList1, List<String> tagList2) {
        Set<String> union = new HashSet<>(tagList1);
        Set<String> intersection = new HashSet<>(tagList1);
        union.addAll(tagList2);
        intersection.retainAll(tagList2);
        return union.size()-intersection.size();


//        int n = tagList1.size(); // 3
//        int m = tagList2.size(); // 4
//
//        if (n * m == 0) {
//            return n + m;
//        }
//
//        int[][] d = new int[n + 1][m + 1]; // [4][5] [i][j]代表 list1 i-1之前，list2 j-1 之前的相似度
//        for (int i = 0; i < n + 1; i++) {  // 边界，最大
//            d[i][0] = i;
//        }
//
//        for (int j = 0; j < m + 1; j++) {  // 边界，最大
//            d[0][j] = j;
//        }
//        // i-1，j-1 子串 的距离不会超过i或 不会超过j
//        // tagList1  tagList2   tagList2
//        //         d c b a         a b c d
//        // a
//        // b
//        // c
//        //
//        //
//        // 0 1 2 3 4             0 1 2 3 4
//        // 1 1 2 3 3                   1 0 1 2 3
//        // 2 2 2                    2
//        // 3 3                     3
//
//        // (...)a  (...)b  依赖于(...) (...)+1、 (...)a (...)+1、（...） (...)b +1;
//        // (...)a  (...)a       (...) (...)、  (...)a  (...)、(...)  (...)a
//
//        for (int i = 1; i < n + 1; i++) { // 行遍历
//            for (int j = 1; j < m + 1; j++) {
//                int left = d[i - 1][j] + 1; // up + 1
//                int down = d[i][j - 1] + 1;// left + 1
//                int up_left = d[i - 1][j - 1]; // up and left
//                if (!Objects.equals(tagList1.get(i - 1), tagList2.get(j - 1))) {  // [i][j]
//                    d[i][j] = Math.min(left, Math.min(down, up_left+1));
//                } else {
//                    d[i][j] = d[i - 1][j - 1];
//                }
//            }
//        }
//        return d[n][m];
    }


    /**
     * 编辑距离算法（用于计算最相似的两个字符串）
     * 原理：https://blog.csdn.net/DBC_121/article/details/104198838
     *
     * @param word1
     * @param word2
     * @return
     */
    public static int minDistance(String word1, String word2) {
        int n = word1.length();
        int m = word2.length();

        if (n * m == 0) {
            return n + m;
        }

        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i < n + 1; i++) {
            d[i][0] = i;
        }

        for (int j = 0; j < m + 1; j++) {
            d[0][j] = j;
        }

        for (int i = 1; i < n + 1; i++) {
            for (int j = 1; j < m + 1; j++) {
                int left = d[i - 1][j] + 1;
                int down = d[i][j - 1] + 1;
                int left_up = d[i - 1][j - 1];
                if (word1.charAt(i - 1) != word2.charAt(j - 1)) {
                    d[i][j] = Math.min(left, Math.min(down, left_up+1));
                } else {
                    d[i][j] = d[i - 1][j - 1];
                }
            }
        }
//        for (int i = 0; i < n+1; i++) {
//            for (int j = 0; j < m+1; j++) {
//                System.out.print(d[i][j]+" ");
//            }
//            System.out.println();
//        }
        return d[n][m];
    }

    /**
     * 判断对象中除某个字段（比如id）和serialVersion字段外其他字段是否为null
     *
     * @param obj
     * @param idFieldName
     * @return
     */
    public static boolean isOtherFieldsNull(Object obj, String idFieldName) {
        Field[] fields = obj.getClass().getDeclaredFields();

        for (Field field : fields) {
            if (!field.getName().equals(idFieldName) && !field.getName().equals("serialVersionUID")) {
                field.setAccessible(true); // 确保我们可以访问private字段
                try {
                    Object value = field.get(obj);
                    if (value != null) {
                        return false;
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }

        return true;
    }
}
