package layers.transportLayer;

import layers.ApplicationLayerInterface;

public class SendingThreadSecurity extends Thread {

    private int destination;
    private ApplicationLayerInterface applicationLayer;

    public SendingThreadSecurity(int destination, ApplicationLayerInterface applicationLayer) {
        this.destination = destination;
        this.applicationLayer = applicationLayer;
    }

    public void run() {
        if (!applicationLayer.alreadyReceivedRSA(destination)) {
            applicationLayer.getTransportLayer().reliableDeliveryN(destination, 1, Integer.toString(applicationLayer.getRSA().getN()));
        } else {
            System.out.println("Already have " + destination + "'s RSA key.");
        }
        int attempts = 0;
        while (!applicationLayer.getTransportLayer().getRSAforN().containsKey(destination) || !applicationLayer.alreadyReceivedRSA(destination)) {
            try {
                Thread.sleep(300);
                attempts++;
                if (attempts > 1000) {
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (!applicationLayer.alreadyReceivedCBC(destination)) {
            applicationLayer.getTransportLayer().reliableDeliveryCBC(destination, 1, applicationLayer.getCBC().getBytes());
        } else {
            System.out.println("Already have " + destination + "'s CBC key.");
        }
    }

}
