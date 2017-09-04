package com.jvmtop;

import joptsimple.OptionSet;

public class Config {
    private final Integer pid;
    private final int numberOfDisplayedThreads;
    private final int threadNameDisplayWidth;
    private final boolean threadLimitEnabled;

    Config(OptionSet args) {
        if (args.hasArgument("pid")) {
            pid = (Integer) args.valueOf("pid");
        } else if (args.nonOptionArguments().size() > 0) {
            //to support PID as non option argument
            pid = Integer.valueOf((String) args.nonOptionArguments().get(0));
        } else {
            pid = null;
        }
        threadLimitEnabled = !args.has("disable-threadlimit");
        numberOfDisplayedThreads = readInt(args, "threadlimit", 30);
        threadNameDisplayWidth = readInt(args, "threadnamewidth", 65);
    }

    private static int readInt(OptionSet args, String p_name, int p_defaultValue) {
        Integer value = args.hasArgument(p_name)? (Integer) args.valueOf(p_name) : null;
        return (value != null)? value : p_defaultValue;
    }

    public Integer getPid() { return pid; }

    public int getThreadNameDisplayWidth() { return threadNameDisplayWidth; }
    public int getNumberOfDisplayedThreads() { return numberOfDisplayedThreads; }
    public boolean isDisplayedThreadLimit() { return threadLimitEnabled; }
}
