import argparse
import os
import time
from pathlib import Path
from openai import OpenAI
from pydantic import BaseModel, Field
from pydantic.json_schema import SkipJsonSchema
from typing import List, Union
import instructor
from instructor.multimodal import Image

HTML_HEAD = '''
<html>
<head>
<style>
img { max-width: 200px; }

.photo {
  max-width:370px;
  vertical-align: top;
  display: inline-block;
  border: solid 1px gray;
  background-color: lightgray;
  padding: 0 0 0 0;
  margin: 2px;
  font-family: 'Luxi Sans';
}

.photo img {
  float: left;
  margin-right: 4px;
}


</style>
</head>
<body>
'''

HTML_FOOT = '</body></html>'


class PhotoDescription(BaseModel):
    """
    Requested photo description.
    """
    description: str = Field(description="A short description of the photograph.")
    dominant_color: str = Field(description="This picture in one color.")
    image_path: SkipJsonSchema[Union[Path, None]] = None


client = instructor.from_openai(
    OpenAI(
        base_url="http://localhost:11434/v1",
        api_key="gemma3:4b",
    ),
    mode=instructor.Mode.JSON,
)


def query_photo(path):
    resp = client.chat.completions.create(
        model="gemma3:4b",
        messages=[
            {
                "role": "user",
                "content": ["Here is a photograph by me.",
                            Image.from_path(path),
                            "Study this image carefully and give me a short description of its contents."],
            }
        ],
        response_model=PhotoDescription,
    )
    resp.image_path = path
    return resp


def html_report(output_file, data: List[PhotoDescription]):
    with open(output_file, 'w') as out:
        out.write(HTML_HEAD)
        for photo in data:
            out.write("<div class='photo'>")
            out.write(f"<img src='{photo.image_path}' />")
            out.write(f"<span>{photo.description}</span>")
            out.write("</div>")
        out.write(HTML_FOOT)


def do_dir(directory):
    t0 = time.clock_gettime_ns(time.CLOCK_BOOTTIME)
    imagepath = Path(directory)
    descs = []
    for imagefile in os.listdir(imagepath):
        desc = query_photo(imagepath / imagefile)
        print ( desc )
        descs.append(desc)
    t1 = time.clock_gettime_ns(time.CLOCK_BOOTTIME)
    html_report('/tmp/day.html', descs)
    print(f'*** Took {(t1 - t0) / 1e9}s for {len(descs)} photos.')


def main():
    pars = argparse.ArgumentParser(
        prog='inqu',
        description='Ask a LLM via running ollama for descriptions for photographs in a directory.'
    )
    pars.add_argument('directory')
    pars.add_argument('--html', action='store_true', help='HTML report (human readable)')
    pars.add_argument('--json', action='store_true', help='JSON report (machine readable)')

    args = pars.parse_args()


if __name__ == '__main__':
    main()
