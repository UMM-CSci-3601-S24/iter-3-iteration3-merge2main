package umm3601.host;

import org.mongojack.Id;
import org.mongojack.ObjectId;

@SuppressWarnings({ "VisibilityModifier" })
public class StartedHunt {

  @ObjectId
  @Id
  @SuppressWarnings({ "MemberName" })
  public String _id;
  public String accessCode;
  public CompleteHunt completeHunt;
  public Boolean status;
}
