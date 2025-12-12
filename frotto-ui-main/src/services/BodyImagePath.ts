export const urlToS3Image = (fileName: string) => {
  return process.env.REACT_APP_S3_URL + fileName;
};
