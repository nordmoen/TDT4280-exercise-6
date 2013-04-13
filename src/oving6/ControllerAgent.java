package oving6;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.ArrayList;

@SuppressWarnings("serial")
public class ControllerAgent extends Agent {

	private final AID[] others = {new AID("kari", AID.ISLOCALNAME),
			new AID("knut", AID.ISLOCALNAME), new AID("truls", AID.ISLOCALNAME)};

	protected void setup() {
		addBehaviour(new ObstacleAvoidanceBehaviour(this));
	}

	protected void takeDown() {}
}

class ObstacleAvoidanceBehaviour extends CyclicBehaviour {
	private Agent myAgent;
	private double maxSpeed = 100.0;
	private boolean recovery = false;
	private final int points = 10;
	private final double maxDiff = 0.01;
	private final ArrayList<Point> prevPos = new ArrayList<Point>(points);

	public ObstacleAvoidanceBehaviour(Agent a) {
		this.myAgent = a;
	}

	private final class Point{
		double x;
		double y;
		public Point(double x, double y){
			this.x = x;
			this.y = y;
		}
	}

	protected double goldDelta(int[] left, int[] right, int camWidth, int camHeight){
		return (5 * maxSpeed *  ((left[1] + left[3]) - (right[1] + right[3])))/(camWidth * camHeight);
	}
	protected int[] objectRecog(int[][] image, int start){
		int[] result = {0, 0, 0, 0, 0, 0};
		for(int x = start; x < image.length/2 + start; x++){
			for(int y = 0; y < image[0].length; y++){
				result[image[x][y]]++;
			}
		}
		return result;
	}
	
	protected boolean fewObst(int[] left, int[] right){
		return left[4] + left[5] + right[4] + right[5] <= 400;
	}

	public void action() {
		ACLMessage msg = myAgent.receive(); 
		if (msg != null) { 
			String[] sensors = msg.getContent().split(", ");

			double[] distance_sensors = { new Double(sensors[0]).doubleValue(), 
					new Double(sensors[1]).doubleValue() };

			double[] position = { new Double(sensors[2]).doubleValue(), 
					new Double(sensors[3]).doubleValue() };
			if(prevPos.size()*2 > points){
				prevPos.clear();
			}
			prevPos.add(new Point(position[0], position[1]));

			double bearing = new Double(sensors[4]).doubleValue();

			int camera_width = Integer.parseInt(sensors[5]);
			int camera_height = Integer.parseInt(sensors[6]);

			int[][] image = new int[camera_width][camera_height];
			for(int x = 0; x < camera_width; x++)
				for(int y = 0; y < camera_height; y++)
					image[x][y] = Integer.parseInt(sensors[camera_width*y + x + 7]);
			double[] motor_speeds = {-25, 25};

			/*
			 * Implement your behaviour here, by setting the motor speeds. Below is a simple obstacle
			 * avoidance behaviour. The maximum speed of the robot motors is [-100,100], this is limited
			 * on the Webots side.
			 */
			int[] left = objectRecog(image, 0);
			int[] right = objectRecog(image, 32);
			double goldDelta = goldDelta(left, right, camera_width, camera_height);
			if(goldDelta != 0.0){
				motor_speeds[0] = maxSpeed - goldDelta;
				motor_speeds[1] = maxSpeed + goldDelta;
			}

			double distance_delta = distance_sensors[0] - distance_sensors[1];

			if(distance_delta != 0){
				motor_speeds[0] = 100/2+distance_delta;
				motor_speeds[1] = 100/2-distance_delta;
			}
			/*
			 * Recovery functionality START
			 * Recovery tries to avoid situations where two robots are hung up
			 * on each other, the recovery action will try to turn the robot
			 * around until it is clear in front of the robot, clear is described
			 * as less than 10% of the screen filled with friends or enemies
			 */
			if(prevPos.size() >= points/2){
				recovery = true;
				for(int i = prevPos.size() - 1; i > 0; i--){
					Point one = prevPos.get(i);
					Point two = prevPos.get(i-1);
					double x = Math.abs(one.x - two.x);
					double y = Math.abs(one.y - two.y);
					if(x > maxDiff || y > maxDiff){
						recovery = false;
						break;
					}
				}
			}

			if(recovery){
				if(fewObst(left, right)){
					prevPos.clear();
					recovery = false;
				}else{
					motor_speeds[0] = maxSpeed/2;
					motor_speeds[1] = -maxSpeed/2;
					
				}
			}
			/*
			 * Recovery functionality END
			 */

			ACLMessage reply = msg.createReply(); 
			reply.setContent(motor_speeds[0] + ", " + motor_speeds[1]);
			myAgent.send(reply);
		}
		else
			block();
	}
}