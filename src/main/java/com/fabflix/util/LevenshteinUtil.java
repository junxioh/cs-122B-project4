package main.java.com.fabflix.util;

public class LevenshteinUtil {
    public static int distance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            for (int j = 0; j <= b.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j; // 如果a为空，插入b的所有字符
                } else if (j == 0) {
                    dp[i][j] = i; // 如果b为空，删除a的所有字符
                } else {
                    dp[i][j] = Math.min(dp[i - 1][j] + 1, // 删除
                            Math.min(dp[i][j - 1] + 1, // 插入
                                    dp[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1))); // 替换
                }
            }
        }
        return dp[a.length()][b.length()];
    }
}
