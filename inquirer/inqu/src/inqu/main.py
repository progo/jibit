#!/usr/bin/env python
import argparse
import os
import json
import time
from pathlib import Path
from openai import OpenAI
from pydantic import BaseModel, Field
from pydantic.json_schema import SkipJsonSchema
from typing import List, Union
import instructor
from instructor.processing.multimodal import Image


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
    # dominant_color: str = Field(description="This picture in one color.")
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


def html_report(outputdir, data: List[PhotoDescription]):
    with open(os.path.join(outputdir, 'report.html'), 'w') as out:
        out.write(HTML_HEAD)
        for photo in data:
            out.write("<div class='photo'>")
            out.write(f"<img src='{photo.image_path}' />")
            out.write(f"<span>{photo.description}</span>")
            out.write("</div>")
        out.write(HTML_FOOT)

def json_report(outputdir, data: List[PhotoDescription]):
    dumpy = [item.model_dump(mode='json') for item in data]

    if outputdir:
        with open(os.path.join(outputdir, 'report.json'), 'w') as out:
            json.dump(dumpy, out)
    else:
        print(json.dumps(dumpy))


def write_descfile(desc, output):
    with open(output, 'w') as out:
        pod = desc.model_dump(mode='json')
        json.dump(pod, out)


def do_dir(directory):
    """Go through the directory, skip files that are not JPEG or PNG.
    Also skip files that have a descfile already.
    """
    # t0 = time.clock_gettime_ns(time.CLOCK_BOOTTIME)
    imagepath = Path(directory)
    descs = []
    for imagefile in os.listdir(imagepath):
        photo_id, ext = os.path.splitext(imagefile)

        descfile = imagepath / f"{photo_id}.desc.json"
        if descfile.exists():
            continue

        processed_descfile = imagepath / f"{photo_id}.desc.json.proc"
        if processed_descfile.exists():
            continue

        if ext.lower() not in ['.jpg', '.jpeg', '.png']:
            continue

        desc = query_photo(imagepath / imagefile)
        write_descfile(desc, descfile)
        descs.append(desc)

    # t1 = time.clock_gettime_ns(time.CLOCK_BOOTTIME)
    return descs


def main():
    pars = argparse.ArgumentParser(
        prog='inqu',
        description='Ask a LLM via running ollama for descriptions for photographs in a directory.'
    )
    pars.add_argument('directory')
    # pars.add_argument('--html', action='store_true',
    #                   help='Write HTML report (human readable)')
    # pars.add_argument('--json', action='store_true',
    #                   help='Write JSON report (machine readable)')
    # pars.add_argument('--stdout', action='store_true',
    #                   help='JSON output')
    pars.add_argument('--onetime', action='store_true',
                      help='Do not monitor the directory')

    args = pars.parse_args()

    photos_described = 0
    while True:
        descriptions = do_dir(args.directory)

        photos_described += len(descriptions)
        if photos_described > 0 and not descriptions:
            break

        if args.onetime:
            break

        time.sleep(0.5)

    # if args.stdout:
    #     json_report(None, descriptions)
    # if we want to keep these, they'd better be taking
    # their output filenames from the CLI args.
    # if args.json:
    #     json_report(args.directory, descriptions)
    # if args.html:
    #     html_report(args.directory, descriptions)

    print(photos_described)

if __name__ == '__main__':
    main()
