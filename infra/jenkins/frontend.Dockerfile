FROM nginx:1.30.6-alpine
RUN rm -f /usr/share/nginx/html/index.html /usr/share/nginx/html/50x.html
COPY . /usr/share/nginx/html/
