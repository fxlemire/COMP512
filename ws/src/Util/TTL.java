package Util;

import server.ws.ResourceManagerAbstract;

import java.util.Timer;
import java.util.TimerTask;

public class TTL {
    private int _transactionId;
    private ResourceManagerAbstract _rm;
    private Timer _timer;
    private int _delay;

    public TTL(int id, ResourceManagerAbstract rm, int seconds) {
        _transactionId = id;
        _rm = rm;
        _timer = new Timer();
        _delay = seconds;
        _timer.schedule(new RemindTask(), seconds * 1000);
    }

    public void restart() {
        _timer.cancel();
        _timer = new Timer();
        _timer.schedule(new RemindTask(), _delay * 1000);
    }

    public void kill() {
        _timer.cancel();
    }

    class RemindTask extends TimerTask {
        public void run() {
            _timer.cancel();
            _rm.abort(_transactionId);
            this.cancel();
        }
    }
}
