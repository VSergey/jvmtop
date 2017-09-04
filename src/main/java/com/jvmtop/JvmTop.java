/*
 * jvmtop - java monitoring for the command-line
 *
 * Copyright (C) 2013 by Patric Rufflar. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.jvmtop;

import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jvmtop.view.*;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

/**
 * JvmTop entry point class.
 *
 * - parses program arguments
 * - selects console view
 * - prints header
 * - main "iteration loop"
 */
public class JvmTop
{
    private static final Logger logger = Logger.getLogger("jvmtop");
    private static final String VERSION = "1.0";
    private final static String CLEAR_TERMINAL_ANSI_CMD =
            new String(
                    new byte[] { (byte) 0x1b, (byte) 0x5b, (byte) 0x32, (byte) 0x4a, (byte) 0x1b, (byte) 0x5b, (byte) 0x48 }
            );

    private final int maxIterations;
    private final Double delay;
    private Boolean supportsSystemAverage;
    private java.lang.management.OperatingSystemMXBean localOSBean;

    private static OptionParser createOptionParser() {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(Arrays.asList("help", "?", "h"), "shows this help").forHelp();
        parser.acceptsAll(Arrays.asList("n", "iteration"),"jvmtop will exit after n output iterations").withRequiredArg().ofType(Integer.class);
        parser.acceptsAll(Arrays.asList("d", "delay"),"delay between each output iteration").withRequiredArg().ofType(Double.class);
        parser.acceptsAll(Arrays.asList("p", "pid"),"PID to connect to").withRequiredArg().ofType(Integer.class);
        parser.accepts("stat", "start stat view at the specified jvm");
        parser.accepts("sysinfo", "outputs diagnostic information");
        parser.accepts("verbose", "verbose mode");
        parser.accepts("threadlimit", "sets the number of displayed threads in detail mode").withRequiredArg().ofType(Integer.class);
        parser.accepts("disable-threadlimit", "displays all threads in detail mode");
        parser.accepts("threadnamewidth", "sets displayed thread name length in detail mode (defaults to 30)").withRequiredArg().ofType(Integer.class);

        return parser;
    }

    public static void main(String[] p_args) throws Exception {
        Locale.setDefault(Locale.US);
        OptionParser parser = createOptionParser();
        OptionSet args = parser.parse(p_args);
        if (args.has("help")) {
            System.out.println("jvmtop - java monitoring for the command-line");
            if (System.getProperty("os.name").contains("Windows")) {
                System.out.println("Usage: jvmtop.bat [options...] [PID]");
            } else {
                System.out.println("Usage: jvmtop.sh [options...] [PID]");
            }
            System.out.println("");
            parser.printHelpOn(System.out);
            System.exit(0);
        }
        if (args.has("sysinfo")) {
            outputSystemProps();
            System.exit(0);
        }
        if (args.has("verbose")) {
            fineLogging();
            logger.setLevel(Level.ALL);
            logger.fine("Verbosity mode.");
        }
        double delay = 1.0;
        if (args.hasArgument("delay")) {
            delay = (Double) (args.valueOf("delay"));
            if (delay < 0.1d) {
                throw new IllegalArgumentException("Delay cannot be set below 0.1");
            }
        }
        Integer iterations = args.hasArgument("n")? (Integer) args.valueOf("n") : -1;
        JvmTop jvmTop = new JvmTop(delay, iterations);
        Config config = new Config(args);
        if (config.getPid() == null) {
            jvmTop.run(new VMOverviewView(config));
        } else {
            if(args.has("stat")) {
                jvmTop.run(new VMDetailStatView(config));
            } else {
                jvmTop.run(new VMDetailView(config));
            }
        }
    }

    public JvmTop(Double p_delay, int iterations) {
        localOSBean = ManagementFactory.getOperatingSystemMXBean();
        delay = p_delay;
        maxIterations = iterations;
    }

    private static void fineLogging() {
        //get the top Logger:
        Logger topLogger = java.util.logging.Logger.getLogger("");

        // Handler for console (reuse it if it already exists)
        Handler consoleHandler = null;
        //see if there is already a console handler
        for (Handler handler : topLogger.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                //found the console handler
                consoleHandler = handler;
                break;
            }
        }
        if (consoleHandler == null) {
            //there was no console handler found, create a new one
            consoleHandler = new ConsoleHandler();
            topLogger.addHandler(consoleHandler);
        }
        //set the console handler to fine:
        consoleHandler.setLevel(java.util.logging.Level.FINEST);
    }

    private static void outputSystemProps() {
        for (Object key : System.getProperties().keySet()) {
            System.out.println(key + "=" + System.getProperty(key + ""));
        }
    }

    private void run(final ConsoleView view) throws Exception {
        try {
            System.setOut(new PrintStream(new BufferedOutputStream(
                    new FileOutputStream(FileDescriptor.out)), false));
            int iterations = 0;
            registerShutdown(view);
            while (!view.shouldExit()) {
                if (maxIterations > 1 || maxIterations == -1) {
                    if (view.isClearingRequired()) {
                        clearTerminal();
                    }
                }
                if (view.isTopBarRequired()) {
                    printTopBar();
                }
                view.printView();
                System.out.flush();
                iterations++;
                if (iterations >= maxIterations && maxIterations > 0) {
                    break;
                }
                view.sleep((int) (delay * 1000));
            }
        } catch (NoClassDefFoundError e) {
            e.printStackTrace(System.err);

            System.err.println("");
            System.err.println("ERROR: Some JDK classes cannot be found.");
            System.err.println("       Please check if the JAVA_HOME environment variable has been set to a JDK path.");
            System.err.println("");
        }
    }

    private static void registerShutdown(final ConsoleView view) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                System.out.print("Finish execution ... ");
                view.last();
                System.out.println("done!");
            } catch (Exception e) {
                System.err.println("Failed to run last in shutdown");
                e.printStackTrace();
            }
        }));
    }

    private void clearTerminal() {
        if (System.getProperty("os.name").contains("Windows")) {
            System.out.print("\033[H\033[2J");
            System.out.flush();
        } else if (System.getProperty("jvmtop.altClear") != null) {
            System.out.print('\f');
        } else {
            System.out.print(CLEAR_TERMINAL_ANSI_CMD);
        }
    }

    private void printTopBar() {
        System.out.printf(" JvmTop %s - %8tT, %6s, %2d cpus, %15.15s", VERSION, new Date(), localOSBean.getArch(),
                localOSBean.getAvailableProcessors(), localOSBean.getName() + " " + localOSBean.getVersion());

        if (supportSystemLoadAverage() && localOSBean.getSystemLoadAverage() != -1) {
            System.out.printf(", load avg %3.2f%n", localOSBean.getSystemLoadAverage());
        } else {
            System.out.println();
        }
        System.out.println();
    }

    private boolean supportSystemLoadAverage() {
        if (supportsSystemAverage == null) {
            try {
                supportsSystemAverage = (localOSBean.getClass().getMethod("getSystemLoadAverage") != null);
            } catch (Throwable e) {
                supportsSystemAverage = false;
            }
        }
        return supportsSystemAverage;
    }
}
