(ns clojure-graphs-presentation-demo.indexes
  (:require
    [com.wsscode.pathom.core :as p]
    [com.wsscode.pathom.connect :as pc]
    [camel-snake-kebab.core :as csk]
    [clojure-graphs-presentation-demo.indexes.github :as github]
    [clojure-graphs-presentation-demo.secrets :as secrets]
    [clojure.set :as set]
    [clj-http.client :as http]))

(def indexes (atom {}))
(defmulti resolve-fn pc/resolver-dispatch)

(def defresolver (pc/resolver-factory resolve-fn indexes))

(def env
  {:youtube.api/token secrets/youtube-token})

(defn namespace-keys [x ns]
  (into {} (map (fn [[k v]] [(keyword ns (csk/->kebab-case (name k))) v])) x))

(defn youtube [{:keys [youtube.api/token]} path]
  (-> (http/get (str "https://www.googleapis.com/youtube/v3" path "&key=" token)
        {:as :auto})
      :body))

(defn adapt-video [{:keys [id snippet]}]
  (-> snippet
      (select-keys [:title :description :publishedAt :channelId :channelTitle])
      (namespace-keys "youtube.video")
      (assoc :youtube.video/id id)
      (set/rename-keys {:youtube.video/channel-id    :youtube.channel/id
                        :youtube.video/channel-title :youtube.channel/title})))

(defresolver `youtube-video-by-id
  {::pc/input  #{:youtube.video/id}
   ::pc/output [:youtube.video/id
                :youtube.video/published-at
                :youtube.video/title
                :youtube.video/description
                :youtube.channel/id
                :youtube.channel/title]}
  (fn [env {:keys [youtube.video/id]}]
    (some-> (youtube env (str "/videos?part=snippet&id=" id))
            :items first
            (adapt-video))))


(defn adapt-comment [{:keys [snippet]}]
  (-> snippet :topLevelComment :snippet
      (select-keys [:likeCount :publishedAt :canRate :textDisplay :videoId :authorDisplayName])
      (namespace-keys "youtube.comment")
      (assoc :youtube.comment/id (-> snippet :topLevelComment :id))
      (set/rename-keys {:youtube.comment/video-id :youtube.video/id})))

(defresolver `youtube-video-comments
  {::pc/input  #{:youtube.video/id}
   ::pc/output [{:youtube.video/comments [:youtube.comment/like-count
                                          :youtube.comment/published-at
                                          :youtube.comment/can-rate
                                          :youtube.comment/text-display
                                          :youtube.comment/id
                                          :youtube.comment/author-display-name
                                          :youtube.video/id]}]}
  (fn [env {:keys [youtube.video/id]}]
    (some->> (youtube env (str "/commentThreads?part=snippet&videoId=" id))
             :items
             (mapv adapt-comment)
             (hash-map :youtube.video/comments))))

(defn adapt-related-video [{:keys [id] :as video}]
  (-> (adapt-video video)
      (assoc :youtube.video/id (:videoId id))))

(defresolver `youtube-video-related
  {::pc/input  #{:youtube.video/id}
   ::pc/output [{:youtube.video/related [:youtube.video/title
                                         :youtube.video/description
                                         :youtube.video/published-at
                                         :youtube.video/id
                                         :youtube.channel/id
                                         :youtube.channel/title]}]}
  (fn [env {:keys [youtube.video/id]}]
    (some->> (youtube env (str "/search?part=snippet&maxResults=25&relatedToVideoId=" id "&type=video"))
             :items
             (mapv adapt-related-video)
             (hash-map :youtube.video/related))))

(defresolver `youtube-next-video
  {::pc/input  #{:youtube.video/related}
   ::pc/output [{:youtube.video/next-video [:youtube.video/title
                                            :youtube.video/description
                                            :youtube.video/published-at
                                            :youtube.video/id
                                            :youtube.channel/id
                                            :youtube.channel/title]}]}
  (fn [_ {:keys [youtube.video/related]}]
    {:youtube.video/next-video (first related)}))
















;;;; GraphQL


(comment
  (github/load-index!))


(defmethod resolve-fn `github/github-resolver [env input]
  (github/gql-resolver env input))

(swap! indexes pc/merge-indexes @github/indexes)


(def video->repo
  {"2V1FtfBDsLU" "richhickey"
   "nlT45ikSEOE" "awkay"})

(defresolver `youtube-video-github-author
  {::pc/input  #{:youtube.video/id}
   ::pc/output [{:clojure.days.graph/video-author-github
                 [:github.user/login]}]}
  (fn [_ {:keys [youtube.video/id]}]
    (if-let [login (get video->repo id)]
      {:clojure.days.graph/video-author-github
       {:github.user/login login}})))
