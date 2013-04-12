import java.util.Arrays;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.core.AID;

public class ControllerAgent extends Agent {
    
    protected void setup() {
	addBehaviour(new ObstacleAvoidanceBehaviour(this));
    }

    protected void takeDown() {}
}

class ObstacleAvoidanceBehaviour extends CyclicBehaviour {
    private Agent myAgent;

    public ObstacleAvoidanceBehaviour(Agent a) {
	this.myAgent = a;
    }

    public void action() {
	ACLMessage msg = myAgent.receive(); 
	if (msg != null) { 
	    String[] sensors = msg.getContent().split(", ");

	    double[] distance_sensors = { new Double(sensors[0]).doubleValue(), 
					  new Double(sensors[1]).doubleValue() };

	    double[] position = { new Double(sensors[2]).doubleValue(), 
				  new Double(sensors[3]).doubleValue() };

	    double bearing = new Double(sensors[4]).doubleValue();

	    int camera_width = Integer.parseInt(sensors[5]);
	    int camera_height = Integer.parseInt(sensors[6]);
	    
	    int[][] image = new int[camera_width][camera_height];
	    for(int x = 0; x < camera_width; x++)
		for(int y = 0; y < camera_height; y++)
		    image[x][y] = Integer.parseInt(sensors[camera_width*y + x + 7]);

	    /*
	     * Implement your behaviour here, by setting the motor speeds. Below is a simple obstacle
	     * avoidance behaviour. The maximum speed of the robot motors is [-100,100], this is limited
	     * on the Webots side.
	     */

	    double distance_delta = distance_sensors[0] - distance_sensors[1];

	    double[] motor_speeds = {0.0, 0.0};
	    motor_speeds[0] = 100/2+distance_delta;
	    motor_speeds[1] = 100/2-distance_delta;

	    ACLMessage reply = msg.createReply(); 
	    reply.setContent(motor_speeds[0] + ", " + motor_speeds[1]);
	    myAgent.send(reply);
	}
	else
	    block();
    }
}