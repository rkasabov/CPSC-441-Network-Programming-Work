import java.io.IOException;
import java.util.TimerTask;

public class DijkstraHandler extends TimerTask {
	
	private Router router;
	
	public  DijkstraHandler(Router router) {
		
		this.router = router;

	}

	@Override
	public void run() {
		
		router.processUpdateRoute();

		
	}
	
	

}
