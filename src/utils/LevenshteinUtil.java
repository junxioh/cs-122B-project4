package utils;

public class LevenshteinUtil {
    public static int distance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            for (int j = 0; j <= b.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j; // 濡傛灉a涓虹┖锛屾彃鍏鐨勬墍鏈夊瓧绗?                } else if (j == 0) {
                    dp[i][j] = i; // 濡傛灉b涓虹┖锛屽垹闄鐨勬墍鏈夊瓧绗?                } else {
                    dp[i][j] = Math.min(dp[i - 1][j] + 1, // 鍒犻櫎
                            Math.min(dp[i][j - 1] + 1, // 鎻掑叆
                                    dp[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1))); // 鏇挎崲
                }
            }
        }
        return dp[a.length()][b.length()];
    }
}
