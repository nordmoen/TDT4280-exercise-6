package oving6;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.FIPAAgentManagement.FailureException;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.ContractNetInitiator;
import jade.proto.ContractNetResponder;

import java.util.ArrayList;
import java.util.Vector;

@SuppressWarnings("serial")
public class ControllerAgent extends Agent {

	public static final AID[] others = {new AID("kari", AID.ISLOCALNAME),
			new AID("knut", AID.ISLOCALNAME), new AID("truls", AID.ISLOCALNAME)};

	protected void setup() {
		ObstacleAvoidanceBehaviour oab = new ObstacleAvoidanceBehaviour(this);
		addBehaviour(oab);
		addBehaviour(new Responder(this, ContractNetResponder.createMessageTemplate("test"), oab));
	}

	protected void takeDown() {}
}

final class Initiator extends ContractNetInitiator{
	private static final long serialVersionUID = 1L;

	public Initiator(Agent a, ACLMessage cfp) {
		super(a, cfp);
	}

	@SuppressWarnings("rawtypes")
	@Override
	protected void handleAllResponses(Vector responses, Vector acceptances) {
		super.handleAllResponses(responses, acceptances);
		double min = Double.MAX_VALUE;
		ACLMessage minMsg = null;
		for(Object o : acceptances){
			ACLMessage m = (ACLMessage)o;
			if(m.getPerformative() == ACLMessage.PROPOSE){
				double newMin = Double.parseDouble(m.getContent());
				if(newMin < min){
					min = newMin;
					minMsg = m;
				}
			}
		}
		if(minMsg != null){
			ACLMessage reply = minMsg.createReply();
			reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
			this.myAgent.send(reply);
		}
	}
}

final class Responder extends ContractNetResponder{
	private static final long serialVersionUID = 1L;
	private final ObstacleAvoidanceBehaviour oab;

	public Responder(Agent a, MessageTemplate mt, ObstacleAvoidanceBehaviour o) {
		super(a, mt);
		this.oab = o;
	}

	@Override
	protected ACLMessage handleCfp(ACLMessage cfp) throws RefuseException,
	FailureException, NotUnderstoodException {
		ACLMessage reply = cfp.createReply();
		System.out.println("HandleCFP!");
		if(!oab.busy){
			String[] sens = cfp.getContent().split(",");
			double[] coord = {Double.parseDouble(sens[0]), Double.parseDouble(sens[1])};
			double[] curr = oab.position;
			double dist = distance(coord, curr);
			//This might be horribly wrong!
			double degs = Math.abs(Math.toDegrees(Math.atan2(coord[1] - curr[1], coord[0] - curr[0])));
			degs = Math.abs(oab.bearing - degs);

			reply.setPerformative(ACLMessage.PROPOSE);
			reply.setContent((dist + degs) + "");
		}else{
			reply.setPerformative(ACLMessage.REFUSE);
		}
		return reply;
	}

	private double distance(double[] dest, double[] curr){
		return Math.sqrt(Math.pow(dest[0] - curr[0], 2) + Math.pow(dest[1] - curr[1], 2));
	}

	@Override
	protected ACLMessage handleAcceptProposal(ACLMessage cfp,
			ACLMessage propose, ACLMessage accept) throws FailureException {
		System.out.println("Handle accept");
		String[] sens = cfp.getContent().split(",");
		double[] coord = {Double.parseDouble(sens[0]), Double.parseDouble(sens[1])};
		oab.busyPosition[0] = coord[0];
		oab.busyPosition[1] = coord[1];
		oab.busy = true;
		//This might be horrible:
		oab.busyBearing = Math.abs(Math.toDegrees(Math.atan2(coord[1] - oab.position[1], coord[0] - oab.position[0])));
		return super.handleAcceptProposal(cfp, propose, accept);
	}
}

final class ObstacleAvoidanceBehaviour extends CyclicBehaviour {
	private static final long serialVersionUID = 1L;
	private Agent myAgent;
	private double maxSpeed = 100.0;
	private boolean recovery = false;
	private final int points = 10;
	private final double maxDiff = 2;
	private final double minBearingDiff = 2;
	private final int minGold = 50;
	private final ArrayList<Point> prevPos = new ArrayList<Point>(points);

	//STATIC methods to be used by the contract net
	public double bearing = -1;
	public double[] position = {0, 0};
	public boolean busy = false;
	public double busyBearing = -1;
	public double[] busyPosition = {0, 0};

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
		return objectRecog(image, start, image.length/2, 0, image[0].length);
	}

	protected int[] objectRecog(int[][] image, int xStart, int xEnd, int yStart, int yEnd){
		int[] result = {0, 0, 0, 0, 0, 0};
		for(int x = xStart; x < xEnd; x++){
			for(int y = yStart; y < yEnd; y++){
				result[image[x][y]]++;
			}
		}
		return result;
	}

	protected boolean fewObst(int[] left, int[] right){
		return left[4] + left[5] + right[4] + right[5] <= 400;
	}

	protected double[] estimateCoordinate(double[] current, double vertDist,
			double horDist, double bearing){
		//TODO: Finish this method so it estimates the coordinates
		double[] result = {0, 0};
		result[0] = current[0] + vertDist;
		result[1] = current[1] + horDist;
		return result;
	}

	protected void handleWebotsMessage(ACLMessage msg){
		String[] sensors = msg.getContent().split(", ");

		double[] distance_sensors = { new Double(sensors[0]).doubleValue(), 
				new Double(sensors[1]).doubleValue() };

		position[0] = new Double(sensors[2]).doubleValue(); 
		position[1] = new Double(sensors[3]).doubleValue();
		if(prevPos.size()*2 > points){
			prevPos.clear();
		}
		prevPos.add(new Point(position[0], position[1]));

		bearing = new Double(sensors[4]).doubleValue();

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

		int maxGold = -1;
		int foundX = -1;
		int foundY = -1;
		for(int x = 0; x < camera_width - camera_width / 4; x += camera_width / 4){
			for(int y = 0; y < camera_height - camera_height / 4; y += camera_height / 4){
				int[] objs = objectRecog(image, x, x + camera_width / 4, y, y + camera_height / 4);
				if(objs[1] + objs[3] > maxGold){
					foundX = x;
					foundY = y;
					maxGold = objs[1] + objs[3];
				}
			}
		}
		if(foundX != -1 && foundY != -1){
			double horDistance = -1;
			double vertDistance = -1; 
			switch (foundX) {
			case 0:
				horDistance = -15;
				break;
			case 16:
				horDistance = -10;
				break;
			case 32:
				horDistance = 10;
				break;
			case 48:
				horDistance = 15;
				break;
			}
			switch (foundY) {
			case 0:
				vertDistance = 25;
				break;
			case 16:
				vertDistance = 20;
				break;
			case 32:
				vertDistance = 15;
				break;
			case 48:
				vertDistance = 10;
				break;
			}
			double[] dest = estimateCoordinate(position, vertDistance, horDistance, bearing);
			ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
			cfp.setContent(dest[0] + "," + dest[1]);
			cfp.setProtocol("test");
			for(AID a : ControllerAgent.others){
				cfp.addReceiver(a);
			}
			new Initiator(myAgent, cfp).action();
		}
		int[] left = objectRecog(image, 0);
		int[] right = objectRecog(image, camera_width/2);
		double goldDelta = goldDelta(left, right, camera_width, camera_height);
		int golds = left[1] + left[3] + right[1] + right[3];
		double bb = bearing - busyBearing;
		if(goldDelta != 0.0 && busy && Math.abs(bb) < minBearingDiff && golds > minGold){
			//We are contracted to go to a coordinate and we are on the right course
			motor_speeds[0] = maxSpeed - goldDelta;
			motor_speeds[1] = maxSpeed + goldDelta;
		}else if(busy && Math.abs(bb) > minBearingDiff){
			//We are contracted to go to a coordinate, but our bearing is not
			//correct
			//If bb is positive we need to turn to the right else left
			if(bb > 0){
				motor_speeds[0] = maxSpeed;
				motor_speeds[1] = -maxSpeed;
			}else{
				motor_speeds[0] = -maxSpeed;
				motor_speeds[1] = maxSpeed;
			}
		}else if(golds < minGold){
			//There is so little gold in front of us that we should consider it
			//mission complete
			busy = false;
			busyBearing = -1;
			busyPosition[0] = -1;
			busyPosition[1] = -1;
		}

		double distance_delta = distance_sensors[0] - distance_sensors[1];

		if(distance_delta != 0 && busy){
			//If we are not busy then we are standing still and there is no
			//need to avoid anything
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
		if(prevPos.size() >= points/2 && busy){
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

	public void action() {
		ACLMessage msg = myAgent.receive();
		if(msg != null){
			switch (msg.getPerformative()) {
			case ACLMessage.INFORM:
				handleWebotsMessage(msg);
				break;
			default:
				System.out.println("Got a message could not handle!");
				System.out.println("\t" + msg.getPerformative());
				System.out.println("\t" + msg.getContent());
				break;
			}
		}else{
			block();
		}
	}
}