package com.limpygnome.parrot.component.session;

import com.limpygnome.parrot.event.DatabaseChangingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Used to keep references to data, used by the front-end, to avoid being garbage collected.
 *
 * This opens up the potential of memory leaks.
 *
 * Notes:
 * - This archive is consumed by other services and is not injected.
 * - All data is wiped when opening/closing a database.
 */
@Service
public class SessionService implements DatabaseChangingEvent
{
    private static final Logger LOG = LoggerFactory.getLogger(SessionService.class);

    private Map<String, Object> store;

    public SessionService()
    {
        store = new HashMap<>();
    }

    public synchronized void reset()
    {
        store.clear();
        LOG.debug("wiped session data");
    }

    public synchronized void put(String key, Object value)
    {
        store.put(key, value);
        LOG.debug("added value - key: {}", key);
    }

    public synchronized Object get(String key)
    {
        return store.get(key);
    }

    public synchronized Object remove(String key)
    {
        Object object = store.remove(key);
        LOG.debug("removed value - key: {}", key);
        return object;
    }

    @Override
    public synchronized void eventDatabaseChanged(boolean open)
    {
        reset();
    }

}
