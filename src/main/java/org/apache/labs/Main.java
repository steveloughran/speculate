package org.apache.labs;

public class Main {


  private static final int ATTEMPTS = 8000;
  private static final int SOURCE_ARRAY_SIZE = 0xfeed;
  private static int[] data = new int[SOURCE_ARRAY_SIZE];
  private static boolean[] boolData = new boolean[SOURCE_ARRAY_SIZE];
  private static final int REDIRECT_ARRAY_SIZE = 0x8000;
  private static int[] redirect = new int[REDIRECT_ARRAY_SIZE];

  public static void main(String[] args) {
    int attempts = ATTEMPTS;
    if (args.length > 0) {
      attempts = Integer.valueOf(args[0]);
    }

    for (int i = 0; i < REDIRECT_ARRAY_SIZE; i++) {
      redirect[i] = i;
    }

    for (int i = 0; i < SOURCE_ARRAY_SIZE; i++) {
      data[i] = i & 0xff;
      boolData[i] = (i & 0x01) != 0;
    }
    for (int i = 0; i < attempts; i++) {
      int e = codeblock(i % REDIRECT_ARRAY_SIZE, data, redirect);
      final String a = attempt2(i, boolData);
      System.out.printf("%d -> (%d, %s)\n", i, e, a);
    }
  }

  private static int codeblock(int iter, int[] src, int[] redir) {
    int d = src[iter];
    int e = redir[0 +((d & 1) << 8)];
    return e;
  }

  final static String srcA = "stringA";
  final static String srcB = "stringB";

  private static String attempt2(int iter, boolean[] src) {
    String ref = srcB;
    boolean d = src[iter];
    ref = d ? srcA : srcB;
    return ref;
  }



}
