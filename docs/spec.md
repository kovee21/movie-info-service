Java developer test
A.) Movie info service
Write a single REST api which uses 2 different public APIs (http://www.omdbapi.com/ and
https://www.themoviedb.org/documentation/api) to retrieve information about a given
movie. By design, the fast response of this interface will be the primary goal. The
secondary goal is to store the search pattern for future statistics purposes.
The number of predicted queries cause the need to use a cache mechanism to avoid the
risk of high load.
There should be two parameters: one to specify which API is to be used and the other to
specify the movie title. i.e. http://localhost:8080/movies/{movieTitle}?api={apiName}
Expected output:
A list of all matching movies, with year + director.
Response:
{"movies":[{"Title":"Countdown","Year":"1967","Director":["Robert
Altman"]},{{"Title":"Count down now" ,"Year":"2100"
,"Director":["John Doe","Jane Doe"]}]
}
omdbapi.com:
1. Search movie
   http://www.omdbapi.com/?s=Avengers&apikey=<<api key>>
2. Get detail of a movie http://www.omdbapi.com/?t={Specific title
   of movie}&apikey=<<api key>>
3. http://www.omdbapi.com/?i={imdbID}&apikey=<<api key>>
   themoviedb.org:
1. Search movie:https://api.themoviedb.org/3/search/movie?api_key=<<api
   key>>&query=Avengers&include_adult=true
2. Get detail of a movie: https://api.themoviedb.org/3/movie/{movie id}?api_key=<<api key>>
3. Get movie credits: https://api.themoviedb.org/3/movie/{movie id}/credits?api_key=<<api key>>
   Please try to pay attention to the different responses of the APIs, regarding performance (not only
   from user perspective, but technical and maintaining perspective as well), user-friendly aspects.
   Please write the code to a production standard that you think would be acceptable for deployment
   on our stack.
   Stack:
   Platform: Spring Boot, Hibernate
   Caching: Redis
   Database: MySQL
   Toolchain, testing: Maven, JUnit, Mockito