FROM node:24-alpine
WORKDIR /app
COPY package.json server.mjs healthcheck.mjs ./
ENV HOST=0.0.0.0 PORT=8787
USER node
EXPOSE 8787
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 CMD ["node", "healthcheck.mjs"]
CMD ["node", "server.mjs"]
