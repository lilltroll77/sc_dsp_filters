// Copyright (c) 2011, XMOS Ltd, All rights reserved
// This software is freely distributable under a derivative of the
// University of Illinois/NCSA Open Source License posted in
// LICENSE.txt and at <http://github.xcore.com/>

import java.io.*;
import java.lang.Math;

class MakeFIR {
    final double pi = Math.PI;

    final int NOWINDOW = 0;
    final int HANN = 1;
    final int HAMMING = 2;
    final int GAUSSIAN = 3;
    final int BLACKMAN = 4;

// Window function from http://en.wikipedia.org/wiki/Window_function
    
    double sqr(double x) {
        return x*x;
    }

    double windowValue(int w, int n, int N) {
        switch(w) {
        case HANN:    return 0.5 * (1 - Math.cos(2 * n * pi / (N-1.0)));
        case HAMMING: return 0.54 - 0.46 * Math.cos(2 * n * pi / (N-1.0));
        case GAUSSIAN: {
            double sigma = 0.4;
            return Math.exp(-0.5*sqr( (n-(N-1.0)/2.0) / (sigma * (N-1.0)/2.0) ));
        }
        case BLACKMAN: {
            double alpha = 0.16;
            double a0 = (1-alpha)/2;
            double a1 = 0.5;
            double a2 = alpha/2;
            return a0 - a1*Math.cos(2*pi*n/(N-1.0))+a2*Math.cos(4*pi*n/(N-1.0));
        }
        case NOWINDOW: return 1.0;
        }
        return 1.0;
    }
    
    
    double sinc(double x, double fc) {
        if (x == 0) {
            return 2*fc;
        } else {
            return Math.sin(2*fc*pi*x)/(pi*x);
        }
    }
    
    double lp(double fc, int w, int n, int N) {
        double s = sinc(n-(N>>1), fc);
        double wi;
        wi = windowValue(w, n, N);
        return s*wi;
    }

// even cooler trick from http://mpastell.com/2010/01/18/fir-with-scipy/
// to convert low pass to high pass.

    double hp(double fc, int w, int n, int N) {
        double l = lp(fc, w, n, N);
        if (n != (N>>1)) {
            return -l;
        } else {
            return 1-l;
        }
    }

/* Band stop, from same website */

    double bs(double fcl, double fch, int w, int n, int N) {
        double l = lp(fcl, w, n, N);
        double h = hp(fch, w, n, N);
        return l+h;
    }
    
/* Band pass, from same website */

    double bp(double fcl, double fch, int w, int n, int N) {
        double b = bs(fcl, fch, w, n, N);
        if (n != (N>>1)) {
            return -b;
        } else {
            return 1-b;
        }
    }


// check http://www.dspguide.com/ch17/1.htm For arbitrary response FIR.


    void usage() {
        System.err.print(
                " -low freq            Lowpass filter, with given corner freq\n" +
                " -high freq           Highpass filter, with given corner freq\n" +
                " -bp freql freqh      Bandpass filter, with given frequencies\n" +
                " -bs freql freqh      Bandstop filter, with given frequencies\n\n" +
                " -gaussian            Gaussian window\n" +
                " -blackman            Blackman window\n" +
                " -hamming             Hamming window (default)\n" +
                " -hann                Hann window\n\n" +
                " -n taps              Number of taps - should be odd\n" +
                " -fs freq             Sample frequency, default 48000\n\n" +
                " -xc sourceFileName   name of source file, default coeffs.xc\n" +
                " -csv csvFileName     name of csv file, default response.csv\n\n" +
                "One of -low, -high, -bp, or -bs must be specified\n" +
                "Outputs are\n" +
                " a source code file that initialises the coefficients table\n" +
                " a CSV file that contains the response curves\n");
        System.exit(0);
    }

    PrintStream fopen_save(String x) {
        try {
            return new PrintStream(x);
        } catch(Exception e) {
            System.err.print( "Cannot open " + x + " for writing\n");
            System.exit(1);
        }                                       
        return null;
    }

    public static void main(String[] args) {
        new MakeFIR(args);
    }

    MakeFIR(String[] args) {
        int i;
        double fs = 48000;
        String sourceFile = "coeffs.xc";
        String responseCurve = "response.csv";
        double [] c = null, e = null;
        int win = HAMMING;
        int type = 0;
        int N = 0;
        double sum = 0;
        double tsum = 0;
        PrintStream fdXC, fdCSV;
        
        for(i = 0; i<args.length; i++) {
            if (args[i].equals("-low")) {
                i++;
            } else if (args[i].equals("-high")) {
                i++;
            } else if (args[i].equals("-bp")) {
                i += 2;
            } else if (args[i].equals("-bs")) {
                i += 2;
            } else if (args[i].equals("-fs")) {
                fs = Double.parseDouble(args[++i]);
            } else if (args[i].equals("-gaussian")) {
                win = GAUSSIAN;
            } else if (args[i].equals("-hamming")) {
                win = HAMMING;
            } else if (args[i].equals("-hann")) {
                win = HANN;
            } else if (args[i].equals("-blackman")) {
                win = BLACKMAN;
            } else if (args[i].equals("-n")) {
                N = Integer.parseInt(args[++i]);
                c = new double[N];
                e = new double[N+160];
                if ((N&1) == 0) {
                    System.err.println("N must be odd\n");
                }
            } else if (args[i].equals("-xc")) {
                sourceFile = args[++i];
            } else if (args[i].equals("-csv")) {
                responseCurve = args[++i];
            } else {
                System.err.print("Option " + args[i]);
                usage();
            }
            
        }
        if (c == null) {
            System.err.println("N must be specified\n");
            usage();
        }
        fdXC = fopen_save(sourceFile);
        fdCSV = fopen_save(responseCurve);
        
        double freq = 0, freqh = 0;
        
        for(int k = 0; k<args.length; k++) {
            if (args[k].equals("-low")) {
                freq = Double.parseDouble(args[++k])/fs;
                for( i = -80; i < 80+N; i++) {
                    if (i >=0 && i < N) {
                        c[i] += lp(freq, win, i, N);
                    }
                    e[i+80] += lp(freq, NOWINDOW, i, N);
                }
            } else if (args[k].equals("-high")) {
                freq = Double.parseDouble(args[++k])/fs;
                for( i = -80; i < 80+N; i++) {
                    if (i >=0 && i < N) {
                        c[i] += hp(freq, win, i, N);
                    }
                    e[i+80] += hp(freq, NOWINDOW, i, N);
                }
            } else if (args[k].equals("-bp")) {
                freq = Double.parseDouble(args[++k])/fs;
                freqh = Double.parseDouble(args[++k])/fs;
                for( i = -80; i < 80+N; i++) {
                    if (i >=0 && i < N) {
                        c[i] += bp(freq, freqh, win, i, N);
                    }
                    e[i+80] += bp(freq, freqh, NOWINDOW, i, N);
                }
            } else if (args[k].equals("-bs")) {
                freq = Double.parseDouble(args[++k])/fs;
                freqh = Double.parseDouble(args[++k])/fs;
                for( i = -80; i < 80+N; i++) {
                    if (i >=0 && i < N) {
                        c[i] += bs(freq, freqh, win, i, N);
                    }
                    e[i+80] += bs(freq, freqh, NOWINDOW, i, N);
                }
            } else if (args[k].equals("-fs")) {
                ++k;
            } else if (args[k].equals("-n")) {
                ++k;
            } else if (args[k].equals("-xc")) {
                ++k;
            } else if (args[k].equals("-csv")) {
                ++k;
            }
        }
        sum = 0;
        for( i = -80; i < 80+N; i++) {
            if (i == (N>>1)) continue;
            if (i >= N || i < 0) {
                sum += sqr(e[i+80]);
            } else {
                sum += sqr(e[i+80] - c[i]);
            }
            tsum += sqr(e[i+80]);
        }
        sum = Math.sqrt(sum)/Math.sqrt(tsum);
        System.out.print("Error: " + (sum*100) + "%\n");
        if (sum > 0.20) {
            System.out.print("    More taps?\n");
        }
        
        
        fdXC.print("//Generated code - do not edit.\n\n" +
                   "int coeff[" + N + "] = {\n");
        for( i = 0; i < N; i++) {
            fdXC.print( " " + ((int) Math.floor(c[i] * (1<<24) + 0.5)) + ", // "+ c[i] +" \n"); 
        }
        fdXC.print( "};\n");

// freq response, from http://www.dspguru.com/dsp/faqs/fir/properties
        
        double omega;
        double factor = Math.sqrt(Math.sqrt(Math.sqrt(Math.sqrt(Math.sqrt(2.0)))));
        fdCSV.print("Frequency,Magnitude,dB\n");
        for(omega = 50; omega < fs/2; omega = omega * factor) {
            double sumr = 0;
            double sumi = 0;
            double mag;
            
            for(i = 0; i < N; i++) {
                double o = omega/fs * 2 * pi;
                sumr += c[i] * Math.cos(i*o);
                sumi += c[i] * Math.sin(i*o);
            }
            mag = Math.sqrt(sumi*sumi + sumr*sumr);
            fdCSV.print(omega + "," + mag + "," + (20*Math.log10(mag)) + "\n");
        }
    }
}