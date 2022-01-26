// used to pass boolean notification print flag to ROCimp
public class VolatileWrapper <T> {
        public volatile T v;

        public VolatileWrapper(T v) {
            this.v = v;
        };
}
