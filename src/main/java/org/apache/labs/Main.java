package org.apache.labs;

public class Main {


  public static final int ATTEMPTS = 8000;
  private static volatile int volatile_counter = 0;

  private static final int SOURCE_ARRAY_SIZE = 0xfeed;
  static int[] data = new int[SOURCE_ARRAY_SIZE];
  public static final int REDIRECT_ARRAY_SIZE = 0x8000;
  static int[] redirect = new int[REDIRECT_ARRAY_SIZE];

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
    }
    for (int i = 0; i < attempts; i++) {
      int e = codeblock(i % REDIRECT_ARRAY_SIZE, data, redirect);
      System.out.printf("%d -> %d\n", i, e);
    }
  }

  private static int codeblock(int iter, int[] src, int[] redir) {
    int e = 0;

    if (iter < SOURCE_ARRAY_SIZE) {
      // add some or operation just to help identify this var
      int d = src[iter | 0xf3];
      e = redir[d];
      // here to make looking through the ASM easier
      e ^= 0x7777;
    }
    return e;

  }
}
