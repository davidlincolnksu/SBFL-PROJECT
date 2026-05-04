package main.java.sbfl;

public enum Formula {

    TARANTULA {
        public double compute(int ef, int ep, int nf, int np) {
            double a = ef / (double)(ef + nf + 1e-9);
            double b = ep / (double)(ep + np + 1e-9);
            return a / (a + b + 1e-9);
        }
    },

    OCHIAI {
        public double compute(int ef, int ep, int nf, int np) {
            return ef / Math.sqrt((ef + ep + 1e-9) * (ef + nf + 1e-9));
        }
    },

    JACCARD {
        public double compute(int ef, int ep, int nf, int np) {
            return ef / (double)(ef + ep + nf + 1e-9);
        }
    },

    ZOLTAR {
        public double compute(int ef, int ep, int nf, int np) {
            if (ef == 0) return 0;
            return ef / (ef + ep + nf + (10000.0 * nf * ep / ef));
        }
    };

    public abstract double compute(int ef, int ep, int nf, int np);
}