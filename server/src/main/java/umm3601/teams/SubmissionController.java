package umm3601.teams;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.mongojack.JacksonMongoCollection;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import umm3601.Controller;
import umm3601.startedHunts.StartedHunt;

public class SubmissionController implements Controller {

  private static final String API_SUBMISSION = "/api/submissions/{id}";
  private static final String API_SUBMISSIONS_BY_TEAM = "/api/submissions/team/{teamId}";
  private static final String API_SUBMISSIONS_BY_TASK = "/api/submissions/task/{taskId}";
  private static final String API_SUBMISSIONS_BY_TEAM_AND_TASK = "/api/submissions/team/{teamId}/task/{taskId}";
  private static final String API_SUBMISSIONS_BY_STARTEDHUNT = "/api/submissions/startedHunt/{startedHuntId}";
  private static final String API_SUBMISSION_GET_PHOTO = "/api/submissions/{id}/photo";
  private static final String PHOTOS = "/photos/{photoPath}";
  private static final String SERVER_PHOTOS = "http://localhost:4567/photos/";

  private final JacksonMongoCollection<Submission> submissionCollection;
  private final JacksonMongoCollection<StartedHunt> startedHuntCollection;

  public SubmissionController(MongoDatabase database) {

    submissionCollection = JacksonMongoCollection.builder().build(
        database,
        "submissions",
        Submission.class,
        UuidRepresentation.STANDARD);

    startedHuntCollection = JacksonMongoCollection.builder().build(
        database,
        "startedHunts",
        StartedHunt.class,
        UuidRepresentation.STANDARD);
  }

  /**
   * Creates a new Submission and inserts it into the submission collection.
   *
   * @param taskId    The ID of the task for the submission.
   * @param teamId    The ID of the team making the submission.
   * @param photoPath The path to the photo for the submission.
   * @return The newly created Submission object.
   */
  public Submission createSubmission(String taskId, String teamId, String photoPath) {
    Submission submission = new Submission();
    submission.taskId = taskId;
    submission.teamId = teamId;
    submission.photoPath = photoPath;
    submission.submitTime = new java.util.Date();
    submissionCollection.insertOne(submission);
    return submission;
  }

  /**
   * Overloaded method for getting a submission by ID.
   *
   * @param id The ID of the submission to retrieve.
   * @return The submission with the given ID.
   */
  public Submission getSubmission(String id) {
    return submissionCollection.find(eq("_id", new ObjectId(id))).first();
  }

  /**
   * Retrieves a Submission from the database and sends it as a JSON response.
   *
   * @param ctx a Javalin Context object containing the HTTP request information.
   *            Expects an "id" path parameter.
   *            If a Submission with the matching ID is found, it is sent as a
   *            JSON response with a status of 200 OK.
   */
  public void getSubmission(Context ctx) {
    String id = ctx.pathParam("id");
    Submission submission = submissionCollection.find(eq("_id", new ObjectId(id))).first();
    if (submission != null) {
      ctx.status(HttpStatus.OK);
      ctx.json(submission);
    }
  }

  /**
   * Retrieves all Submissions associated with a specific team from the database.
   *
   * @param ctx a Javalin Context object containing the HTTP request information.
   *            Expects a "teamId" path parameter.
   *            Outputs a JSON array of Submission objects and sets the HTTP
   *            status to OK (200).
   */
  public void getSubmissionsByTeam(Context ctx) {
    String teamId = ctx.pathParam("teamId");
    ArrayList<Submission> teamSubmissions = submissionCollection.find(eq("teamId", teamId)).into(new ArrayList<>());
    ctx.json(teamSubmissions);
    ctx.status(HttpStatus.OK);
  }

  /**
   * Retrieves all Submissions associated with a specific task from the
   * database.
   *
   * @param ctx a Javalin Context object containing the HTTP request information.
   *            Expects a "taskId" path parameter.
   *            Outputs a JSON array of Submission objects and sets the HTTP
   *            status to OK (200).
   */
  public void getSubmissionsByTask(Context ctx) {
    String taskId = ctx.pathParam("taskId");
    List<Submission> taskSubmissions = submissionCollection.find(eq("taskId", taskId)).into(new ArrayList<>());
    ctx.json(taskSubmissions);
    ctx.status(HttpStatus.OK);
  }

  /**
   * Retrieves the first Submission associated with a specific team and task from
   * the database.
   *
   * @param ctx a Javalin Context object containing the HTTP request information.
   *            Expects "teamId" and "taskId" path parameters.
   *            Outputs a JSON object of the first Submission with the matching
   *            teamId and taskId,
   *            or sets the HTTP status to NOT_FOUND (404) if no such Submission
   *            exists.
   */
  public void getSubmissionByTeamAndTask(Context ctx) {
    String teamId = ctx.pathParam("teamId");
    String taskId = ctx.pathParam("taskId");

    Submission submission = submissionCollection.find(eq("teamId", teamId)).filter(eq("taskId", taskId)).first();
    if (submission != null) {
      ctx.json(submission);
      ctx.status(HttpStatus.OK);
    } else {
      ctx.status(HttpStatus.NOT_FOUND);
    }
  }

  /**
   * Retrieves all Submissions associated with a specific started hunt from
   * the database.
   *
   * @param ctx a Javalin Context object containing the HTTP request information.
   *            Expects a "startedHuntId" path parameter.
   *            Outputs a JSON array of Submission objects with the matching
   *            startedHuntId,
   *            or an empty array if no such Submissions exist.
   */
  public void getSubmissionsByStartedHunt(Context ctx) {
    String startedHuntId = ctx.pathParam("startedHuntId");

    // Check if the startedHuntId is valid
    if (!ObjectId.isValid(startedHuntId)) {
      throw new IllegalArgumentException("Invalid startedHuntId: " + startedHuntId);
    }

    // Fetch the startedHunt object
    StartedHunt startedHunt = startedHuntCollection.find(eq("_id", new ObjectId(startedHuntId))).first();

    if (startedHunt == null) {
      ctx.json(new ArrayList<>());
      ctx.status(HttpStatus.OK);
    } else {
      // Get the list of submissionIds from the startedHunt
      List<String> submissionIds = startedHunt.getSubmissionIds();

      // Fetch all submissions with those IDs
      List<Submission> submissions = submissionCollection
          .find(in("_id", submissionIds.stream().map(ObjectId::new).collect(Collectors.toList())))
          .into(new ArrayList<>());

      ctx.json(submissions);
      ctx.status(HttpStatus.OK);
    }
  }

  /**
   * Retrieves a photo associated with a specific submission from the server.
   *
   * @param ctx a Javalin HTTP context
   *
   *            This method takes a Javalin HTTP context as input, which should
   *            contain a path parameter 'id' representing the submission ID.
   *            It attempts to find a submission with the given ID in the
   *            submission collection, and if found, it tries to retrieve the
   *            associated photo file from the 'photos/' directory.
   *            If the photo file exists, it sends the file as a result with an
   *            HTTP status of OK.
   *            If the photo file does not exist, it sends an empty result with an
   *            HTTP status of NOT FOUND.
   *            If there's an error while accessing the file, it sends an error
   *            message as a result with an HTTP status of INTERNAL SERVER ERROR.
   */
  public void getPhotoFromSubmission(Context ctx) {
    String submissionId = ctx.pathParam("id");
    System.out.println("Server: Received request to get photo for submissionId: " + submissionId);

    Submission submission = submissionCollection.find(eq("_id", new ObjectId(submissionId))).first();

    File photo = new File("photos/" + submission.photoPath);
    if (photo.exists()) {
      try (FileInputStream fis = new FileInputStream(photo)) {
        ctx.result(fis);
        ctx.status(HttpStatus.OK);
      } catch (IOException e) {
        ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Error accessing file: " + e.getMessage());
      }
    } else {
      System.out.println("Server: No photo found for submissionId: " + submissionId);
      ctx.result("");
      ctx.status(HttpStatus.NOT_FOUND);
    }
  }

  /**
   * Deletes a Submission from the database.
   *
   * @param ctx a Javalin Context object containing the HTTP request information.
   *            Expects an "id" path parameter.
   *            If a Submission with the matching ID is found, it is deleted and
   *            the HTTP status is set to NO_CONTENT (204).
   *            If no such Submission is found, a NotFoundResponse is thrown.
   */
  public void deleteSubmission(Context ctx, String id) {
    Submission submission = submissionCollection.find(eq("_id", new ObjectId(id))).first();

    if (submission == null) {
      throw new NotFoundResponse("The requested submission was not found.");
    }

    // Delete the photo associated with the submission
    try {
      Path photoPath = Paths.get("photos/" + submission.photoPath);
      Files.deleteIfExists(photoPath);
    } catch (IOException e) {
      ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Error deleting file: " + e.getMessage());
      return;
    }
    submissionCollection.removeById(id);
    ctx.status(HttpStatus.NO_CONTENT);
  }

  public void deleteSubmissions(ArrayList<String> submissionIds) {
    // Convert submissionIds to a list of ObjectId
    List<ObjectId> objectIds = submissionIds.stream()
        .map(ObjectId::new)
        .collect(Collectors.toList());

    // Create a filter that matches the _id field to any of the submissionIds
    Bson filter = Filters.in("_id", objectIds);

    // Delete all matching documents
    submissionCollection.deleteMany(filter);
  }

  /*
   *
   * ******PHOTO HANDLING******
   *
   */

  public void addPhoto(Context ctx) {
    String id = uploadPhoto(ctx);
    addPhotoPathToSubmission(ctx, id);
    ctx.status(HttpStatus.CREATED);
    ctx.json(Map.of("id", id));
  }

  public String getFileExtension(String filename) {
    int dotIndex = filename.lastIndexOf('.');
    if (dotIndex >= 0) {
      return filename.substring(dotIndex + 1);
    } else {
      return "";
    }
  }

  public String uploadPhoto(Context ctx) {
    var uploadedFile = ctx.uploadedFile("photo");
    if (uploadedFile != null) {
      try (InputStream in = uploadedFile.content()) {

        String id = UUID.randomUUID().toString();

        String extension = getFileExtension(uploadedFile.filename());
        File file = Path.of("photos", id + "." + extension).toFile();
        System.err.println("The path was " + file.toPath());

        Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        ctx.status(HttpStatus.OK);
        return id + "." + extension;
      } catch (IOException e) {
        System.err.println("Error copying the uploaded file: " + e);
        throw new BadRequestResponse("Error handling the uploaded file: " + e.getMessage());
      }
    } else {
      throw new BadRequestResponse("No photo uploaded");
    }
  }

  public void addPhotoPathToSubmission(Context ctx, String photoPath) {
    System.out.println("addPhotoPathToSubmission method called with photoPath: " + photoPath);
    String taskId = ctx.pathParam("taskId");
    String teamId = ctx.pathParam("teamId");
    String startedHuntId = ctx.pathParam("startedHuntId"); // get the startedHuntId from the context
    Submission submission = submissionCollection.find(and(eq("taskId", taskId), eq("teamId", teamId))).first();

    if (submission == null) {
      submission = createSubmission(taskId, teamId, photoPath); // store the new submission
    } else {
      submission.photoPath = photoPath;
      submissionCollection.insertOne(submission);
    }

    // Add the submission's ID to the StartedHunt's submissionIds array
    StartedHunt startedHunt = startedHuntCollection.find(eq("_id", new ObjectId(startedHuntId))).first();
    if (startedHunt != null) {
      startedHunt.submissionIds.add(submission._id); // assuming submissionIds is a List<String>
      startedHuntCollection.save(startedHunt);
    }
  }

  public void deletePhoto(String id, Context ctx) {
    Path filePath = Path.of("photos/" + id);
    if (!Files.exists(filePath)) {
      ctx.status(HttpStatus.NOT_FOUND);
      throw new BadRequestResponse("Photo with ID " + id + " does not exist");
    }
    try {
      Files.delete(filePath);

      ctx.status(HttpStatus.OK);
    } catch (IOException e) {
      ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
      throw new BadRequestResponse("Error deleting the photo: " + e.getMessage());
    }
  }

  public void getPhoto(Context ctx) {
    String photoPath = ctx.pathParam("photoPath");
    File file = new File("photos/" + photoPath);
    if (file.exists()) {
      try {
        ctx.result(new FileInputStream(file));

      } catch (FileNotFoundException e) {
        ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Error reading file: " + e.getMessage());
      }
    } else {
      ctx.status(HttpStatus.NOT_FOUND).result("Photo not found");
    }
  }

  public void replacePhoto(Context ctx) {
    String taskId = ctx.pathParam("taskId");
    String teamId = ctx.pathParam("teamId");

    // Find the submission
    Submission submission = submissionCollection.find(and(eq("taskId", taskId), eq("teamId", teamId))).first();

    if (submission == null) {
      throw new BadRequestResponse("No submission found for the given taskId and teamId");
    }

    // Delete the old photo
    deletePhoto(submission.photoPath, ctx);

    // Upload a new photo and update the submission
    String newPhotoId = uploadPhoto(ctx);
    submission.photoPath = newPhotoId;

    // Update the submission in the database
    submissionCollection.updateOne(eq("_id", submission._id),
        new Document("$set", new Document("photoPath", newPhotoId)));

    ctx.status(HttpStatus.OK);
    ctx.json(Map.of("id", newPhotoId));
  }

  public String getPhotoFromServer(Submission submission) throws FileNotFoundException {
    if (submission == null || submission.photoPath == null) {
      throw new FileNotFoundException("Photo not found");
    }
    String photoUrl = SERVER_PHOTOS + submission.photoPath;
    return photoUrl;
  }

  /*
   *
   * ******END PHOTO HANDLING******
   *
   */

  @Override
  public void addRoutes(Javalin server) {
    server.get(API_SUBMISSION, this::getSubmission);
    server.get(API_SUBMISSIONS_BY_TEAM, this::getSubmissionsByTeam);
    server.get(API_SUBMISSIONS_BY_TASK, this::getSubmissionsByTask);
    server.get(API_SUBMISSIONS_BY_TEAM_AND_TASK, this::getSubmissionByTeamAndTask);
    server.get(API_SUBMISSIONS_BY_STARTEDHUNT, this::getSubmissionsByStartedHunt);
    server.get(API_SUBMISSION_GET_PHOTO, this::getPhotoFromSubmission);
    server.delete(API_SUBMISSION, ctx -> deleteSubmission(ctx, ctx.pathParam("id")));
    server.post(API_SUBMISSION, this::addPhoto);
    server.put(API_SUBMISSION, this::replacePhoto);
    server.get(PHOTOS, this::getPhoto);
  }

}
