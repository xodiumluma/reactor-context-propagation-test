package reactor.context.propagation.test;

import io.micrometer.context.ThreadLocalAccessor;

public class ObservationThreadLocalAccessor implements ThreadLocalAccessor<String>{

    public static final String KEY = "micrometer.observation";

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public String getValue() {
        return ObservationThreadLocalHolder.getValue();
    }

    @Override
    public void setValue(String value) {
        ObservationThreadLocalHolder.setValue(value);
    }

    @Override
    public void reset() {
        ObservationThreadLocalHolder.reset();
    }
}
