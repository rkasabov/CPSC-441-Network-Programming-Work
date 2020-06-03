import java.io.IOException;
import java.util.TimerTask;

public class BroadcastHandler extends TimerTask {
	
	private Router router;
	
	public  BroadcastHandler(Router router) {
		
		this.router = router;

	}

	@Override
	public void run() {
		
		try {
			router.processUpdateNeighbor();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		
	}
	
	

}
